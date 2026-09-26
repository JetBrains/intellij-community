// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.writeaheadlog

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

class ByteArrayQueueWriteAheadLogTest {
  val fileWriter: (Path, Long, ByteBuffer) -> Unit = { path, offsetInFile, data ->
    FileChannel.open(path, CREATE, WRITE).use {
      it.write(data, offsetInFile)
    }
  }

  @Test
  fun `flush writes queued byte arrays in order`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    Files.write(file, byteArrayOf(1, 2, 3, 4))

    val writeAheadLog = ByteArrayQueueWriteAheadLog(fileWriter)
    val fileWriter = writeAheadLog.openFor(file)

    fileWriter.write(4, byteArrayOf(5, 6), 0, 2)
    fileWriter.write(1, byteArrayOf(9, 8, 7), 0, 3)

    assertTrue(fileWriter.hasUnfinished())
    assertArrayEquals(byteArrayOf(1, 2, 3, 4), Files.readAllBytes(file))

    writeAheadLog.flush()

    assertFalse(fileWriter.hasUnfinished())
    assertArrayEquals(byteArrayOf(1, 9, 8, 7, 5, 6), Files.readAllBytes(file))
  }

  @Test
  fun `applyUnfinished overlays queued writes without consuming them`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val writeAheadLog = ByteArrayQueueWriteAheadLog(fileWriter)
    val fileWriter = writeAheadLog.openFor(file)

    fileWriter.write(2, byteArrayOf(9, 9, 9), 0, 3)
    fileWriter.write(4, byteArrayOf(7, 7), 0, 2)

    val buffer = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5, 6))
    fileWriter.applyUnfinished(1, 5, buffer, 1)

    assertArrayEquals(byteArrayOf(1, 2, 9, 9, 7, 7), buffer.array())
    assertTrue(fileWriter.hasUnfinished())
  }
}
