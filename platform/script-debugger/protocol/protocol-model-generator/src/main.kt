package org.jetbrains.protocolModelGenerator

import org.jetbrains.io.JsonReaderEx
import org.jetbrains.jsonProtocol.ProtocolSchemaReaderImpl
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files

public fun main(args: Array<String>) {
  val outputDir = args[0]
  val schemaUrl = args[1]
  val bytes: ByteArray
  if (schemaUrl.startsWith("http")) {
    bytes = loadBytes(URL(schemaUrl).openStream())
  }
  else {
    bytes = Files.readAllBytes(FileSystems.getDefault().getPath(schemaUrl))
  }
  val reader = JsonReaderEx(String(bytes, StandardCharsets.UTF_8))
  reader.setLenient(true)
  Generator(outputDir, args[2], args[3]).go(ProtocolSchemaReaderImpl().parseRoot(reader))
}

public fun loadBytes(stream: InputStream): ByteArray {
  val buffer = ByteArrayOutputStream(Math.max(stream.available(), 16 * 1024))
  val bytes = ByteArray(1024 * 20)
  while (true) {
    val n = stream.read(bytes, 0, bytes.size())
    if (n <= 0) {
      break
    }
    buffer.write(bytes, 0, n)
  }
  buffer.close()
  return buffer.toByteArray()
}