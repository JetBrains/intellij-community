package org.jetbrains.jsonProtocol

import org.jetbrains.io.JsonReaderEx
import org.jetbrains.jsonProtocol.ProtocolMetaModel.Root

import java.io.IOException

public trait ProtocolSchemaReader {
  JsonParseMethod
  throws(javaClass<IOException>())
  public fun parseRoot(reader: JsonReaderEx): Root
}