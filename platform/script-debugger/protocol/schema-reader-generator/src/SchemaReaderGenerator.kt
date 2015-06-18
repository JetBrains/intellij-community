package org.jetbrains.jsonProtocol

import org.jetbrains.protocolReader.GenerateConfiguration
import org.jetbrains.protocolReader.generate

fun main(args: Array<String>) {
  generate(if (args.isEmpty()) arrayOf("--output-dir=community/platform/script-debugger/protocol/protocol-model-generator/generated") else args,
          GenerateConfiguration("org.jetbrains.jsonProtocol",
                  "ProtocolSchemaReaderImpl",
                  javaClass<ProtocolSchemaReader>(),
                  javaClass<ProtocolMetaModel>().getDeclaredClasses()))
}