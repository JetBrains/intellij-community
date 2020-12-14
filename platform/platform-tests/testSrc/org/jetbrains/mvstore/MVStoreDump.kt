// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.mvstore

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import org.jetbrains.integratedBinaryPacking.IntBitPacker
import org.jetbrains.mvstore.type.DataType
import org.jetbrains.mvstore.type.LongDataType
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

private val fileOptions = arrayOf(StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)

// caddy file-server --root ~/icon-db
fun main(args: Array<String>) {
  val storeBuilder = MVStore.Builder()
    .backgroundExceptionHandler { e, store -> throw e }
    .readOnly()

  val mapBuilder = MVMap.Builder<Long, ByteArray>()
  mapBuilder.keyType(LongDataType.INSTANCE)
  mapBuilder.valueType(OpaqueValueSerializer())

  val dbFile = Paths.get(args.first())
  val outFile = dbFile.parent.resolve(dbFile.fileName.toString() + ".json")

  val viewer = OpaqueValueSerializer::class.java.getResourceAsStream("tree.html")
    .readAllBytes()
    .toString(Charsets.UTF_8)
    .replace("chart.dataSource.url = \"./file.db.json\"", "chart.dataSource.url = \"./${outFile.fileName}?lastModified=${
      System.currentTimeMillis().toString(
        36)
    }\"")
  Files.writeString(dbFile.parent.resolve("tree.html"), viewer)

  val dotFile = dbFile.parent.resolve(dbFile.fileName.toString() + ".dot")
  Files.newOutputStream(dotFile, *fileOptions).bufferedWriter().use { dotFileWriter ->
    dotFileWriter.write("digraph g {\n")
    dotFileWriter.write("node [shape = record,height=.1];\n")
    val dotWriter = DotWriter(dotFileWriter)
    JsonFactory().createGenerator(Files.newOutputStream(outFile, *fileOptions).bufferedWriter())
      .useDefaultPrettyPrinter().use { writer ->
        //writer.writeStartObject()
        writer.writeStartArray()

        //writer.writeStringField("name", "db")
        //writer.writeArrayFieldStart("children")

        val store = storeBuilder.open(dbFile)
        val mapNames = store.mapNames.asSequence().map { it.toString() }.toList().sorted()

        dotWriter.writer.write("node${dotWriter.counter++}[label = \"${mapNamesToLabel(mapNames)}\"];\n")

        for ((index, mapName) in mapNames.withIndex()) {
          val map = store.openMap(mapName, mapBuilder)
          val rootPage = map.rootPage

          writer.writeStartObject()
          writer.writeStringField("name", mapName)
          writer.writeNumberField("tc", rootPage.totalCount)
          writer.writeArrayFieldStart("children")
          dumpPage(rootPage, writer, "root (${rootPage.keyCount})", dotWriter, 0, index)
          writer.writeEndArray()

          writer.writeEndObject()
        }

        writer.writeEndArray()
        //writer.writeEndObject()
      }
    dotWriter.writer.write("}")
  }
}

private fun mapNamesToLabel(mapNames: List<String>): String {
  val builder = StringBuilder()
  for ((index, name) in mapNames.withIndex()) {
    if (builder.isNotEmpty()) {
      builder.append("|")
    }
    builder.append("<f$index> |$name")
  }
  builder.append("|f${mapNames.size}")
  return builder.toString()
}


private fun dumpPage(page: Page<Long, ByteArray>,
                     writer: JsonGenerator,
                     pageName: String,
                     dotWriter: DotWriter,
                     parentNodeId: Int,
                     pageIndex: Int) {
  val nodeId = dotWriter.counter++
  dotWriter.writer.write("node$nodeId[label = \"${if (page.isLeaf) "leaf" else keysToLabel(page)}\"];\n")

  writer.writeStartObject()
  writer.writeStringField("name", pageName)
  writer.writeNumberField("value", page.keyCount)
  writer.writeNumberField("i", pageIndex)
  if (!page.isLeaf) {
    writer.writeNumberField("tc", page.totalCount)

    writer.writeStringField("keys", keysToTooltip(page))
    writer.writeArrayFieldStart("children")
    for (index in 0 until page.keyCount) {
      dumpPage(page.getChildPage(index), writer, page.getKey(index).toString(), dotWriter, nodeId, index)
    }
    writer.writeEndArray()
  }

  dotWriter.writer.write("\"node$parentNodeId\":f$pageIndex -> \"node$nodeId\"\n")

  writer.writeEndObject()
}

private fun keysToTooltip(page: Page<Long, ByteArray>): String {
  val keyManager = page.keyManager
  val builder = StringBuilder()
  for (index in 0 until keyManager.keyCount) {
    if (builder.isNotEmpty()) {
      builder.append("\n")
    }
    builder.append(keyManager.getKey(index))
  }
  return builder.toString()
}

private fun keysToLabel(page: Page<Long, ByteArray>): String {
  val keyManager = page.keyManager
  val builder = StringBuilder()
  for (index in 0 until keyManager.keyCount) {
    if (builder.isNotEmpty()) {
      builder.append("|")
    }
    builder.append("<f$index> |${keyManager.getKey(index)}")
  }
  builder.append("|<f${keyManager.keyCount}>")
  return builder.toString()
}

private class OpaqueValueSerializer : DataType<ByteArray> {
  override fun createStorage(size: Int): Array<ByteArray?> {
    return arrayOfNulls(size)
  }

  override fun getMemory(obj: ByteArray) = obj.size

  override fun getFixedMemory() = -1

  override fun write(buf: ByteBuf, obj: ByteArray) {
    throw IllegalStateException()
  }

  @Suppress("UNUSED_VARIABLE")
  override fun read(buf: ByteBuf): ByteArray {
    val width = buf.readFloat()
    val height = buf.readFloat()
    val actualWidth = IntBitPacker.readVar(buf)
    val actualHeight = IntBitPacker.readVar(buf)
    val length = actualWidth * actualHeight * 4
    val obj = ByteBufUtil.getBytes(buf, buf.readerIndex(), length)
    buf.readerIndex(buf.readerIndex() + length)
    return obj
  }
}

// graphviz doesn't suitable for large graphs, so, JS solution is used
private class DotWriter(val writer: Writer) {
  var counter = 0
}
