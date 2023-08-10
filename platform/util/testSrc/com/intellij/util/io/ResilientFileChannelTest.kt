// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.testFramework.rules.TempDirectory
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.NonWritableChannelException
import java.nio.file.StandardOpenOption.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

class ResilientFileChannelTest {
  @Rule
  @JvmField
  val tempDirectory: TempDirectory = TempDirectory()

  private val byteBuf1 = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4))
  private val byteBuf2 = ByteBuffer.wrap(byteArrayOf(5, 6, 7, 8))

  @After
  fun clearInterruptedStatus() {
    Thread.interrupted()
  }

  @Test
  fun `without interruption RFC writes and reads as regular FileChannel`() {
    ResilientFileChannel(newTempFile(), READ, WRITE, CREATE).use {
      it.write(byteBuf1)
      it.write(byteBuf2)

      val readBuffer1 = ByteBuffer.allocate(4)
      it.read(readBuffer1, 0)

      val readBuffer2 = ByteBuffer.allocate(4)
      it.read(readBuffer2, 4)

      assertEquals(IOUtil.toString(byteBuf1), IOUtil.toString(readBuffer1))
      assertEquals(IOUtil.toString(byteBuf2), IOUtil.toString(readBuffer2))
    }
  }

  @Test
  fun `read operations successfully reads its bytes after interrupt`() {
    ResilientFileChannel(newTempFile(), READ, WRITE, CREATE).use {
      it.write(byteBuf1)
      it.write(byteBuf2)

      val readBuffer1 = ByteBuffer.allocate(4)
      it.read(readBuffer1, 0)

      val currentThread = Thread.currentThread()
      currentThread.interrupt()

      val readBuffer2 = ByteBuffer.allocate(4)
      it.read(readBuffer2, 0)

      assertEquals(byteBuf1, readBuffer1)
      assertEquals(byteBuf2, readBuffer2)
    }
  }

  @Test
  fun `2 reads operations successfully read their bytes after interrupt`() {
    ResilientFileChannel(newTempFile(), READ, WRITE, CREATE).use {
      it.write(byteBuf1)
      it.write(byteBuf2)

      val currentThread = Thread.currentThread()
      currentThread.interrupt()

      val readBuffer1 = ByteBuffer.allocate(4)
      it.read(readBuffer1, 0)

      assertTrue(Thread.currentThread().isInterrupted)

      val readBuffer2 = ByteBuffer.allocate(4)
      it.read(readBuffer2, 0)

      assertEquals(byteBuf1, readBuffer1)
      assertEquals(byteBuf2, readBuffer2)
    }
  }

  @Test
  fun `2 reads operations successfully read their bytes via RFC freshly opened after interrupt`() {
    val file = newTempFile()
    ResilientFileChannel(file, READ, WRITE, CREATE).use {
      it.write(byteBuf1)
      it.write(byteBuf2)
    }

    Thread.currentThread().interrupt()

    val readBuffer1 = ByteBuffer.allocate(4)
    val readBuffer2 = ByteBuffer.allocate(4)

    ResilientFileChannel(file, READ, WRITE, CREATE).use {
      it.read(readBuffer1, 0)
      it.read(readBuffer2, 0)
    }

    assertEquals(byteBuf1, readBuffer1)
    assertEquals(byteBuf2, readBuffer2)
  }

  @Test
  fun `attempt to write to read-only RFC throws exception`() {
    val file = newTempFile()
    var secondOperationStarted = false
    var exceptionHappened = false

    try {
      ResilientFileChannel(file, READ).use {
        it.write(byteBuf1)
        secondOperationStarted = true
        it.write(byteBuf2)
      }
      Assert.fail("Should not executed without exception")
    }
    catch (e: NonWritableChannelException) {
      exceptionHappened = true
    }

    assertTrue(exceptionHappened)
    Assert.assertFalse(secondOperationStarted)
  }

  @Test
  fun `independent writes from multiple threads could be read from multiple threads`() {
    val file = newTempFile()
    val threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    fun joinThreads() {
      threadPool.shutdown()
      repeat(60) {
        if (threadPool.awaitTermination(1, SECONDS)) {
          return@repeat
        }
      }
      assertTrue(threadPool.isTerminated)
    }

    try {
      ResilientFileChannel(file, READ, WRITE, CREATE).use { fileHandle ->
        (0..100).map {
          threadPool.submit {
            val threadId = Thread.currentThread().id
            val threadUniqueData = byteArrayOf(threadId.toByte(), threadId.toByte(), threadId.toByte(), threadId.toByte())

            fileHandle.write(ByteBuffer.wrap(threadUniqueData), threadId * threadUniqueData.size)
          }
        }.toList().forEach { it.get(1, MINUTES) }

        Thread.currentThread().interrupt()
        fileHandle.size()
        assertTrue(Thread.interrupted())

        (0..100).map {
          threadPool.submit {
            val threadId = Thread.currentThread().id
            val expectedData = ByteBuffer.wrap(byteArrayOf(threadId.toByte(), threadId.toByte(), threadId.toByte(), threadId.toByte()))
            val actualData = ByteBuffer.allocate(expectedData.capacity())

            assertEquals(
              "All expected bytes are read",
              actualData.capacity(),
              fileHandle.read(actualData, threadId * actualData.capacity())
            )

            assertEquals(
              "Bytes read should be same as written before",
              IOUtil.toString(expectedData),
              IOUtil.toString(actualData.rewind())
            )
          }
        }.toList().forEach { it.get(1, MINUTES) }
      }
    }
    finally {
      joinThreads()
    }
  }

  @Test
  fun `thread interrupted status is not cleared while RFC work around interruption`() {
    val file = newTempFile()
    ResilientFileChannel(file, READ, WRITE, CREATE).use{
      it.write(byteBuf1)
    }

    val readBuffer1 = ByteBuffer.allocate(byteBuf1.capacity())

    Thread.currentThread().interrupt()
    ResilientFileChannel(file, READ, WRITE, CREATE).use {
      it.read(readBuffer1, 0)
    }
    assertTrue(
      "Thread interrupted status is not cleared",
      Thread.currentThread().isInterrupted
    )
  }

  @Test
  fun `RFChannel throws exception if closed before read`() {
    val file = newTempFile()
    val readBuffer1 = ByteBuffer.allocate(byteBuf1.capacity())

    val channel = ResilientFileChannel(file, READ, WRITE, CREATE)
    channel.write(byteBuf1)
    channel.close()
    try {
      channel.read(readBuffer1, 0)
    }catch (e: ClosedChannelException){
      //OK
    }
  }

  private fun newTempFile() = tempDirectory.newFile("test.txt").toPath()
}