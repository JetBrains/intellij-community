// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.testFramework.rules.TempDirectory
import org.junit.After
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.NonWritableChannelException
import java.nio.file.StandardOpenOption
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UnInterruptibleFileChannelTest {
  @Rule
  @JvmField
  val tempDirectory = TempDirectory()

  private val byteBuf1 = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4))
  private val byteBuf2 = ByteBuffer.wrap(byteArrayOf(5, 6, 7, 8))

  @After
  fun clearInterruptedStatus() {
    Thread.interrupted()
  }

  @Test
  fun `test no interruption`() {
    UnInterruptibleFileChannel(newTempFile(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use {
      it.write(byteBuf1)
      it.write(byteBuf2)

      val readBuffer1 = ByteBuffer.allocate(4)
      it.read(readBuffer1, 0)

      val readBuffer2 = ByteBuffer.allocate(4)
      it.read(readBuffer2, 0)

      Assert.assertEquals(byteBuf1, readBuffer1)
      Assert.assertEquals(byteBuf2, readBuffer2)
    }
  }

  @Test
  fun `test interrupted on last operation`() {
    UnInterruptibleFileChannel(newTempFile(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use {
      it.write(byteBuf1)
      it.write(byteBuf2)

      val readBuffer1 = ByteBuffer.allocate(4)
      it.read(readBuffer1, 0)

      val currentThread = Thread.currentThread()
      currentThread.interrupt()

      val readBuffer2 = ByteBuffer.allocate(4)
      it.read(readBuffer2, 0)

      Assert.assertEquals(byteBuf1, readBuffer1)
      Assert.assertEquals(byteBuf2, readBuffer2)
    }
  }

  @Test
  fun `test interrupted in the middle`() {
    UnInterruptibleFileChannel(newTempFile(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use {
      it.write(byteBuf1)
      it.write(byteBuf2)

      val currentThread = Thread.currentThread()
      currentThread.interrupt()

      val readBuffer1 = ByteBuffer.allocate(4)
      it.read(readBuffer1, 0)

      Assert.assertTrue(Thread.currentThread().isInterrupted)

      val readBuffer2 = ByteBuffer.allocate(4)
      it.read(readBuffer2, 0)

      Assert.assertEquals(byteBuf1, readBuffer1)
      Assert.assertEquals(byteBuf2, readBuffer2)
    }
  }

  @Test
  fun `test open existing file`() {
    val file = newTempFile()
    UnInterruptibleFileChannel(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use {
      it.write(byteBuf1)
      it.write(byteBuf2)
    }

    Thread.currentThread().interrupt()

    val readBuffer1 = ByteBuffer.allocate(4)
    val readBuffer2 = ByteBuffer.allocate(4)

    UnInterruptibleFileChannel(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use {
      it.read(readBuffer1, 0)
      it.read(readBuffer2, 0)
    }

    Assert.assertEquals(byteBuf1, readBuffer1)
    Assert.assertEquals(byteBuf2, readBuffer2)
  }

  @Test
  fun `test write to read-only file`() {
    val file = newTempFile()
    var secondOperationStarted = false
    var exceptionHappened = false

    try {
      UnInterruptibleFileChannel(file, StandardOpenOption.READ).use {
        it.write(byteBuf1)
        secondOperationStarted = true
        it.write(byteBuf2)
      }
      Assert.fail("Should not executed without exception")
    }
    catch (e: NonWritableChannelException) {
      exceptionHappened = true
    }

    Assert.assertTrue(exceptionHappened)
    Assert.assertFalse(secondOperationStarted)
  }

  @Test
  fun `test concurrent access`() {
    val file = newTempFile()
    val threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    fun joinThreads() {
      threadPool.shutdown()
      repeat(60) {
        if (threadPool.awaitTermination(1, TimeUnit.SECONDS)) {
          return@repeat
        }
      }
      Assert.assertTrue(threadPool.isTerminated)
    }

    try {
      UnInterruptibleFileChannel(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use { fileHandle ->
        (0..100).map {
          threadPool.submit {
            val threadId = Thread.currentThread().id
            val data = byteArrayOf(threadId.toByte(), threadId.toByte(), threadId.toByte(), threadId.toByte())

            fileHandle.write(ByteBuffer.wrap(data), threadId * 4)
          }
        }.toList().forEach { it.get(1, TimeUnit.MINUTES) }

        Thread.currentThread().interrupt()
        fileHandle.size()
        Assert.assertTrue(Thread.interrupted())

        (0..100).map {
          threadPool.submit {
            val threadId = Thread.currentThread().id
            val expectedData = ByteBuffer.wrap(byteArrayOf(threadId.toByte(), threadId.toByte(), threadId.toByte(), threadId.toByte()))
            val actualData = ByteBuffer.allocate(4)

            Assert.assertEquals(4, fileHandle.read(actualData, threadId * 4))

            Assert.assertEquals(expectedData, actualData.rewind())
          }
        }.toList().forEach { it.get(1, TimeUnit.MINUTES) }
      }
    }
    finally {
      joinThreads()
    }
  }

  private fun newTempFile() = tempDirectory.newFile("test.txt").toPath()
}