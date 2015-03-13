package org.jetbrains.jsonProtocol

import org.jetbrains.protocolReader.ReaderGenerator

fun main(args: Array<String>) = ReaderGenerator.generate(args, ReaderGenerator.GenerateConfiguration("org.jetbrains.jsonProtocol", "ProtocolSchemaReaderImpl", javaClass<ProtocolSchemaReader>(), javaClass<ProtocolMetaModel>().getDeclaredClasses()))