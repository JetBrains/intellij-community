package org.jetbrains.jsonProtocol

import org.jetbrains.io.JsonReaderEx
import org.jetbrains.jsonProtocol.ProtocolMetaModel.Root

public interface ProtocolSchemaReader {
  JsonParseMethod
  public fun parseRoot(reader: JsonReaderEx): Root
}