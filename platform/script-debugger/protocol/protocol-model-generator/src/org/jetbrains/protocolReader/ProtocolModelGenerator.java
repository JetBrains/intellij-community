package org.jetbrains.protocolReader;

import org.jetbrains.io.JsonReaderEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jsonProtocol.ProtocolSchemaReaderImpl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;

public class ProtocolModelGenerator {
  public static void main(String[] args) throws IOException {
    String outputDir = args[0];
    String schemaUrl = args[1];
    byte[] bytes;
    if (schemaUrl.startsWith("http")) {
      bytes = loadBytes(new URL(schemaUrl).openStream());
    }
    else {
      bytes = Files.readAllBytes(FileSystems.getDefault().getPath(schemaUrl));
    }
    JsonReaderEx reader = new JsonReaderEx(new String(bytes, StandardCharsets.UTF_8));
    reader.setLenient(true);
    new Generator(outputDir, args[2], args[3]).go(new ProtocolSchemaReaderImpl().parseRoot(reader));
  }

  @NotNull
  public static byte[] loadBytes(@NotNull InputStream stream) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(stream.available(), 16 * 1024));
    byte[] bytes = new byte[1024 * 20];
    while (true) {
      int n = stream.read(bytes, 0, bytes.length);
      if (n <= 0) {
        break;
      }
      buffer.write(bytes, 0, n);
    }
    buffer.close();
    return buffer.toByteArray();
  }
}