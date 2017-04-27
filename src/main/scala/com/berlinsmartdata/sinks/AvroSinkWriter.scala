package com.berlinsmartdata.sinks

import java.io.IOException

import org.apache.avro.file
import org.apache.avro.Schema
import org.apache.avro.Schema.Type
import org.apache.avro.file.CodecFactory
import org.apache.avro.file.DataFileConstants
import org.apache.avro.file.DataFileWriter
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord

import org.apache.avro.io.DatumWriter
import org.apache.avro.specific.{SpecificDatumWriter, SpecificRecordBase}

import scala.reflect.ClassTag
//import org.apache.flink.core.fs.FSDataOutputStream
import org.apache.flink.streaming.connectors.fs.{StreamWriterBase, Writer}
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat
import org.apache.hadoop.fs.{FSDataOutputStream, FileSystem, Path}


/**
  * Scala custom Avro Writer for Flink
  */
class AvroSinkWriter[T <: SpecificRecordBase : ClassTag] extends StreamWriterBase[T] {

  val serialVersionUID = 1L

  @transient private var outputWriter: Option[DataFileWriter[T]] = None
  @transient private var userDefinedSchema: Option[Schema] = None

  def setSchema(schema: Schema): Unit = {
    userDefinedSchema = Some(schema)
  }

  override def open(fs: FileSystem, path: Path): Unit = {
    super.open(fs, path)

    if (userDefinedSchema.isEmpty)
      throw new IllegalStateException("Avro Schema required to create AvroSinkWriter")

    //val compressionCodec: CodecFactory = getCompressionCodec(properties);
    if (outputWriter.isEmpty)
      buildDatumWriter(getStream)

  }

  override def write(element: T) = {
    if (outputWriter.isEmpty)
      throw new IllegalStateException("AvroSinkWriter has not been opened")

    outputWriter.get.append(element)
  }

  override def duplicate() = new AvroSinkWriter[T]()

  private def buildDatumWriter(outStream: FSDataOutputStream,
                               compressionCodec: Option[CodecFactory] = None,
                               syncInterval: Option[Int] = None): Unit = {

    val writer: DatumWriter[T] = new SpecificDatumWriter[T](userDefinedSchema.get)
    val avroFileWriter = new file.DataFileWriter[T](writer)

    if (compressionCodec.isDefined)
      avroFileWriter.setCodec(compressionCodec.get)
    if (syncInterval.isDefined)
      avroFileWriter.setSyncInterval(syncInterval.get)

    outputWriter = Some(avroFileWriter)
    outputWriter.get.create(userDefinedSchema.get, outStream)

  }

  //  private def getCompressionCodec(conf: Map[String,String]): CodecFactory = {
  //    if (getOrElse(conf, AvroSinkWriter.CONF_COMPRESS, false)) {
  //      int deflateLevel = getInt(conf, AvroSinkWriter.CONF_DEFLATE_LEVEL, CodecFactory.DEFAULT_DEFLATE_LEVEL);
  //      int xzLevel = getInt(conf, AvroSinkWriter.CONF_XZ_LEVEL, CodecFactory.DEFAULT_XZ_LEVEL);
  //
  //      String outputCodec = conf.get(AvroSinkWriter.CONF_COMPRESS_CODEC);
  //
  //      if (DataFileConstants.DEFLATE_CODEC.equals(outputCodec)) {
  //        return CodecFactory.deflateCodec(deflateLevel);
  //      } else if (DataFileConstants.XZ_CODEC.equals(outputCodec)) {
  //        return CodecFactory.xzCodec(xzLevel);
  //      } else {
  //        return CodecFactory.fromString(outputCodec);
  //      }
  //    }
  //    return CodecFactory.nullCodec();
  //  }

}

object AvroSinkWriter {

  lazy val CONF_OUTPUT_SCHEMA = "avro.schema.output"
  lazy val CONF_COMPRESS = FileOutputFormat.COMPRESS
  lazy val CONF_COMPRESS_CODEC = FileOutputFormat.COMPRESS_CODEC
  lazy val CONF_DEFLATE_LEVEL = "avro.deflate.level"
  lazy val CONF_XZ_LEVEL = "avro.xz.level"

  def buildSchema() {}
}