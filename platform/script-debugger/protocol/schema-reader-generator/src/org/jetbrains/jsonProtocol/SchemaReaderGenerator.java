package org.jetbrains.jsonProtocol;

import org.jetbrains.protocolReader.DynamicReader;
import org.jetbrains.protocolReader.ReaderGenerator;

import java.io.IOException;

public class SchemaReaderGenerator extends ReaderGenerator {
  public static void main(String[] args) throws IOException {
    mainImpl(args, new GenerateConfiguration("org.jetbrains.jsonProtocol", "ProtocolSchemaReaderImpl",
                                             new DynamicReader<>(ProtocolSchemaReader.class,
                                                                 ProtocolMetaModel.class.getDeclaredClasses())));
  }
}