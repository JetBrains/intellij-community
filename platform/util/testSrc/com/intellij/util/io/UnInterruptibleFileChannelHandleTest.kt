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

class UnInterruptibleFileChannelHandleTest {
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
    UnInterruptibleFileChannelHandle(newTempFile(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use {
      it.executeOperation { ch -> ch.write(byteBuf1) }
      it.executeOperation { ch -> ch.write(byteBuf2) }

      val readBuffer1 = ByteBuffer.allocate(4)
      it.executeOperation { ch -> ch.read(readBuffer1, 0) }

      val readBuffer2 = ByteBuffer.allocate(4)
      it.executeOperation { ch -> ch.read(readBuffer2, 0) }

      Assert.assertEquals(byteBuf1, readBuffer1)
      Assert.assertEquals(byteBuf2, readBuffer2)
    }
  }

  @Test
  fun `test interrupted on last operation`() {
    val channels = mutableSetOf<FileChannel>()

    UnInterruptibleFileChannelHandle(newTempFile(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use {
      it.executeOperation { ch ->
        ch.write(byteBuf1)
        channels.add(ch)
      }
      it.executeOperation { ch ->
        ch.write(byteBuf2)
        channels.add(ch)
      }

      val readBuffer1 = ByteBuffer.allocate(4)
      it.executeOperation { ch ->
        ch.read(readBuffer1, 0)
        channels.add(ch)
      }

      Assert.assertEquals(1, channels.size)
      val currentThread = Thread.currentThread()
      currentThread.interrupt()

      val readBuffer2 = ByteBuffer.allocate(4)
      it.executeOperation { ch ->
        ch.read(readBuffer2, 0)
        channels.add(ch)
      }

      Assert.assertEquals(2, channels.size)
      Assert.assertEquals(byteBuf1, readBuffer1)
      Assert.assertEquals(byteBuf2, readBuffer2)
    }
  }

  @Test
  fun `test interrupted in the middle`() {
    val channels = mutableSetOf<FileChannel>()

    UnInterruptibleFileChannelHandle(newTempFile(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use {
      it.executeOperation { ch ->
        ch.write(byteBuf1)
        channels.add(ch)
      }
      it.executeOperation { ch ->
        ch.write(byteBuf2)
        channels.add(ch)
      }

      Assert.assertEquals(1, channels.size)
      val currentThread = Thread.currentThread()
      currentThread.interrupt()

      val readBuffer1 = ByteBuffer.allocate(4)
      it.executeOperation { ch ->
        ch.read(readBuffer1, 0)
        channels.add(ch)
      }

      Assert.assertTrue(Thread.currentThread().isInterrupted)

      val readBuffer2 = ByteBuffer.allocate(4)
      it.executeOperation { ch ->
        ch.read(readBuffer2, 0)
        channels.add(ch)
      }

      Assert.assertEquals(3, channels.size)
      Assert.assertEquals(byteBuf1, readBuffer1)
      Assert.assertEquals(byteBuf2, readBuffer2)
    }
  }

  @Test
  fun `test open existing file`() {
    val file = newTempFile()
    UnInterruptibleFileChannelHandle(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use {
      it.executeOperation { ch -> ch.write(byteBuf1) }
      it.executeOperation { ch -> ch.write(byteBuf2) }
    }

    Thread.currentThread().interrupt()

    val readBuffer1 = ByteBuffer.allocate(4)
    val readBuffer2 = ByteBuffer.allocate(4)

    UnInterruptibleFileChannelHandle(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use {
      it.executeOperation { ch -> ch.read(readBuffer1, 0) }
      it.executeOperation { ch -> ch.read(readBuffer2, 0) }
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
      UnInterruptibleFileChannelHandle(file, StandardOpenOption.READ).use {
        it.executeOperation { ch -> ch.write(byteBuf1) }
        secondOperationStarted = true
        it.executeOperation { ch -> ch.write(byteBuf2) }
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
      UnInterruptibleFileChannelHandle(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use { fileHandle ->
        (0..100).map {
          threadPool.submit {
            val threadId = Thread.currentThread().id
            val data = byteArrayOf(threadId.toByte(), threadId.toByte(), threadId.toByte(), threadId.toByte())

            fileHandle.executeOperation {
              it.write(ByteBuffer.wrap(data), threadId * 4)
            }
          }
        }.toList().forEach { it.get(1, TimeUnit.MINUTES) }

        Thread.currentThread().interrupt()
        fileHandle.executeOperation { it.size() }
        Assert.assertTrue(Thread.interrupted())

        (0..100).map {
          threadPool.submit {
            val threadId = Thread.currentThread().id
            val expectedData = ByteBuffer.wrap(byteArrayOf(threadId.toByte(), threadId.toByte(), threadId.toByte(), threadId.toByte()))
            val actualData = ByteBuffer.allocate(4)

            fileHandle.executeOperation {
              Assert.assertEquals(4, it.read(actualData, threadId * 4))
            }

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