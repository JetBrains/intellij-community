// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.protocolReader

import java.nio.file.Files
import java.nio.file.Path

/**
 * A class that makes accurate java source file update. If only header
 * (with source file revision and other comments) changed, the file is left intact.
 * <p>User first writes all the content into a {@link Writer} provided and then
 * calls {@link #update()}.
 */
class FileUpdater(private val file: Path) {
  val builder: StringBuilder = StringBuilder()
  val out: TextOutput = TextOutput(builder)

  fun update() {
    if (builder.length == 0) {
      Files.delete(file)
      return
    }

    val newContent = builder.toString().toByteArray()
    if (Files.exists(file)) {
      if (Files.readAllBytes(file).contentEquals(newContent)) {
        return
      }
    }
    else {
      Files.createDirectories(file.parent)
    }
    Files.write(file, newContent)
  }
}
