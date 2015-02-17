package org.jetbrains.protocolReader

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Arrays

/**
 * A class that makes accurate java source file update. If only header
 * (with source file revision and other comments) changed, the file is left intact.
 * <p>User first writes all the content into a {@link Writer} provided and then
 * calls {@link #update()}.
 */
class FileUpdater(private val file: Path) {
  val builder = StringBuilder()
  val out = TextOutput(builder)

  fun update() {
    if (builder.length() == 0) {
      Files.delete(file)
      return
    }

    val newContent = builder.toString().getBytes(StandardCharsets.UTF_8)
    if (Files.exists(file)) {
      if (Arrays.equals(Files.readAllBytes(file), newContent)) {
        return
      }
    }
    else {
      Files.createDirectories(file.getParent())
    }
    Files.write(file, newContent)
  }
}
