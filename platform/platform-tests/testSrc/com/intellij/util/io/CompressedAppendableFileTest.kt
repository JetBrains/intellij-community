/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import net.jpountz.lz4.LZ4Factory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

class CompressedAppendableFileTest : TestCase() {
  fun testNativeCompression() {
    val nativeDecomp = LZ4Factory.nativeInstance().fastDecompressor()
    assert(nativeDecomp.javaClass.name.contains("JNI"))
    assertFailed {
      nativeDecomp.decompress(ByteArray(3), ByteArray(12))
    }
    assertFailed {
      nativeDecomp.decompress(ByteArray(0), ByteArray(12))
    }
  }

  private fun assertFailed(op: () -> Unit) {
    try {
      op()
      fail()
    }
    catch (ignored: Exception) {

    }
    catch (ignored: AssertionError) {

    }
  }

  @Throws
  fun testCreateParentDirWhenSave() {
    val randomTemporaryPath = FileUtil.generateRandomTemporaryPath().toPath().resolve("Test.compressed")
    try {
      val appendableFile = CompressedAppendableFile(randomTemporaryPath)
      val byteArray: ByteArray = ByteArray(1)
      appendableFile.append(byteArray, 1)
      appendableFile.force()
      FileUtil.delete(randomTemporaryPath.parent)
      appendableFile.append(byteArray, 1)
      appendableFile.dispose()
    } finally {
      FileUtil.delete(randomTemporaryPath.parent)
    }
  }

  fun testSizeUpdateBug() {
    val randomTemporaryPath = FileUtil.generateRandomTemporaryPath().toPath().resolve("Test.compressed")
    try {
      var appendableFile = CompressedAppendableFile(randomTemporaryPath)
      val singleByteArray: ByteArray = ByteArray(1)
      appendableFile.append(singleByteArray, singleByteArray.size)
      appendableFile.dispose()

      appendableFile = CompressedAppendableFile(randomTemporaryPath)

      val multiByteArray: ByteArray = ByteArray(CompressedAppendableFile.PAGE_LENGTH - 1)
      appendableFile.append(multiByteArray, multiByteArray.size)
      appendableFile.dispose()

      appendableFile = CompressedAppendableFile(randomTemporaryPath)
      assertEquals(CompressedAppendableFile.PAGE_LENGTH, appendableFile.length().toInt())
      appendableFile.dispose()
    } finally {
      FileUtil.delete(randomTemporaryPath.parent)
    }
  }

  fun testConcurrencyStress() {
    val randomTemporaryPath = FileUtil.generateRandomTemporaryPath().toPath().resolve("Test.compressed")
    try {
      val appendableFile = CompressedAppendableFile(randomTemporaryPath)
      val max = 1000 * CompressedAppendableFile.PAGE_LENGTH
      val startLatch = CountDownLatch(1)
      val numberOfThreads = 3
      val proceedLatch = CountDownLatch(numberOfThreads)
      val bytesWritten = AtomicLong()

      val writer = {
        startLatch.await()
        try {
          val byteArray: ByteArray = ByteArray(3)
          
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

      val futures: MutableList<Future<*>> = ArrayList()
      for(i in 1..numberOfThreads) {
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

        assertEquals(bytesWritten.get(), appendableFile.length())
        appendableFile.dispose()
        val appendableFile2 = CompressedAppendableFile(randomTemporaryPath)
        assertEquals(bytesWritten.get(), appendableFile2.length())
        appendableFile2.dispose()
      }
      finally {
        thread.get()
        futures.forEach { it.get() }
      }
    }
    finally {
      FileUtil.delete(randomTemporaryPath.parent)
    }
  }
}