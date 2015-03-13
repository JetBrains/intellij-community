package org.jetbrains.jsonProtocol

public trait ProtocolSchemaReader {
  JsonParseMethod
  throws(javaClass<IOException>())
  public fun parseRoot(reader: JsonReaderEx): Root
}