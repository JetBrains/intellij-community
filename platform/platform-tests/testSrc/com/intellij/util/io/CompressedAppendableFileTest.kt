// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

class CompressedAppendableFileTest {
  @Test
  fun testCreateParentDirWhenSave(@TempDir tempDir: Path) {
    val compressedFile = tempDir.resolve("Test.compressed")
    withCompressedAppendableFile(compressedFile) { appendableFile ->
      val byteArray = ByteArray(1)
      appendableFile.append(byteArray, 1)
      appendableFile.force()
      compressedFile.parent.toFile().deleteRecursively()
      appendableFile.append(byteArray, 1)
    }
  }

  @Test
  fun testSizeUpdateBug(@TempDir tempDir: Path) {
    val compressedFile = tempDir.resolve("Test.compressed")
    val singleByteArray = ByteArray(1)
    withCompressedAppendableFile(compressedFile) { appendableFile ->
      appendableFile.append(singleByteArray, singleByteArray.size)
    }

    val multiByteArray = ByteArray(CompressedAppendableFile.PAGE_LENGTH - 1)
    withCompressedAppendableFile(compressedFile) { appendableFile ->
      appendableFile.append(multiByteArray, multiByteArray.size)
    }

    withCompressedAppendableFile(compressedFile) { appendableFile ->
      assertThat(appendableFile.length().toInt()).isEqualTo(CompressedAppendableFile.PAGE_LENGTH)
    }
  }

  @Test
  fun testConcurrencyStress(@TempDir tempDir: Path) {
    val compressedFile = tempDir.resolve("Test.compressed")
    val bytesWritten = AtomicLong()
    withCompressedAppendableFile(compressedFile) { appendableFile ->
      val max = 1000 * CompressedAppendableFile.PAGE_LENGTH
      val startLatch = CountDownLatch(1)
      val numberOfThreads = 3
      val proceedLatch = CountDownLatch(numberOfThreads)

      val writer = {
        startLatch.await()
        try {
          val byteArray = ByteArray(3)
          
          for (i in 1..max) {
            byteArray[0] = (i and 0xFF).toByte()
            byteArray[1] = (i + 1 and 0xFF).toByte()
            byteArray[2] = (i + 2 and 0xFF).toByte()
            appendableFile.append(byteArray, byteArray.size)
            bytesWritten.addAndGet(byteArray.size.toLong())
            //if (i % 100 == 0) TimeoutUtil.sleep(1);
          }
        }
        finally {
          proceedLatch.countDown()
        }
      }

      val futures = ArrayList<Future<*>>()
      repeat(numberOfThreads) {
        futures.add(AppExecutorUtil.getAppExecutorService().submit(writer))
      }

      val flusher = {
        startLatch.await()
        while (proceedLatch.count != 0L) {
          UIUtil.pump()
        }
      }
      val thread = AppExecutorUtil.getAppExecutorService().submit(flusher)
      try {
        startLatch.countDown()
        proceedLatch.await()

        assertThat(appendableFile.length()).isEqualTo(bytesWritten.get())
      }
      finally {
        thread.get()
        futures.forEach { it.get() }
      }
    }

    withCompressedAppendableFile(compressedFile) { appendableFile ->
      assertThat(appendableFile.length()).isEqualTo(bytesWritten.get())
    }
  }
}

private inline fun <T> withCompressedAppendableFile(path: Path, action: (CompressedAppendableFile) -> T): T {
  val appendableFile = CompressedAppendableFile(path)
  try {
    return action(appendableFile)
  }
  finally {
    appendableFile.dispose()
  }
}