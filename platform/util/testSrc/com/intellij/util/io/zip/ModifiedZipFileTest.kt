// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.zip

import com.intellij.util.io.directoryContent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.appendBytes

class ModifiedZipFileTest {

  fun createTempZipFile(big: Boolean): Path {
    val dir = directoryContent {
      zip("test.zip") {
        for (i in 0 until 5) {
          val text = if (big) "entry$i" + "_".repeat(1024 * 64) else "entry$i"
          file("entry$i", text = text)
        }
      }
    }.generateInTempDir()
    return dir.resolve("test.zip")
  }

  fun `zip file with random bytes after central directory`(numberOfBytes: Int, isBigFile: Boolean) {
    val file = createTempZipFile(isBigFile)
    val randomBytes = ByteArray(numberOfBytes) { 1 } // definitely will not be recognized as cen signature
    file.appendBytes(randomBytes)
    val parsedArchive = JBZipFile(file.toFile())
    Assertions.assertTrue(parsedArchive.getEntry("entry3").inputStream.readAllBytes().decodeToString().startsWith("entry3"))
  }

  @ParameterizedTest
  @ValueSource(ints = [0, 1, 5, 128, 1024, 64 * 1024 - 1, 64 * 1024, 64 * 1024 + 1])
  fun `small zip file with random bytes after central directory`(numberOfBytes: Int) {
    return `zip file with random bytes after central directory`(numberOfBytes, false)
  }

  @ParameterizedTest
  @ValueSource(ints = [0, 1, 5, 128, 1024, 64 * 1024 - 1, 64 * 1024, 64 * 1024 + 1])
  fun `big zip file with random bytes after central directory`(numberOfBytes: Int) {
    return `zip file with random bytes after central directory`(numberOfBytes, true)
  }
}