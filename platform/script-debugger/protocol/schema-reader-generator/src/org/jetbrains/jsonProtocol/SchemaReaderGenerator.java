package org.jetbrains.jsonProtocol;

import org.jetbrains.protocolReader.ReaderGenerator;

import java.io.IOException;

public class SchemaReaderGenerator {
  public static void main(String[] args) throws IOException {
    ReaderGenerator.generate(args, new ReaderGenerator.GenerateConfiguration<>("org.jetbrains.jsonProtocol", "ProtocolSchemaReaderImpl", ProtocolSchemaReader.class, ProtocolMetaModel.class.getDeclaredClasses()));
  }
}