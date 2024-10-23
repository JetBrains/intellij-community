// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.zip

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.io.directoryContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

class ZipFileThreadSafetyTest {

  fun createTempZipFile(numEntries: Int): Path {
    val dir = directoryContent {
      zip("test.zip") {
        for (i in 0 until numEntries) {
          file("entry$i", text = "entry$i");
        }
      }
    }.generateInTempDir()
    return dir.resolve("test.zip")
  }

  @ParameterizedTest
  @ValueSource(ints = [1, 2, 10, 50, 100])
  fun `test concurrent access to zip file`(numThreads: Int) = timeoutRunBlocking() {
    val numEntries = 100
    val zipFile = createTempZipFile(numEntries)
    val zipFileHandle = JBZipFile(zipFile.toFile())
    repeat(numThreads) {
      launch(Dispatchers.Default) {
        repeat(500) {
          val randomEntry = (Math.random() * numEntries).toInt()
          val entryName = "entry$randomEntry"
          zipFileHandle.getEntry(entryName).inputStream.use { stream ->
            Assertions.assertEquals(entryName, stream.readAllBytes().decodeToString())
          }
        }
      }
    }
  }
}