package org.jetbrains.protocolReader;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * A class that makes accurate java source file update. If only header
 * (with source file revision and other comments) changed, the file is left intact.
 * <p>User first writes all the content into a {@link Writer} provided and then
 * calls {@link #update()}.
 */
class FileUpdater {
  private final Path file;
  final StringBuilder builder;
  final TextOutput out;

  FileUpdater(Path file) {
    this.file = file;
    builder = new StringBuilder();
    out = new TextOutput(builder);
  }

  void update() throws IOException {
    byte[] newContent = builder.toString().getBytes(StandardCharsets.UTF_8);
    if (Files.exists(file)) {
      byte[] oldContent = Files.readAllBytes(file);
      if (Arrays.equals(oldContent, newContent)) {
        return;
      }
    }
    else {
      Files.createDirectories(file.getParent());
    }
    Files.write(file, newContent);
  }
}
