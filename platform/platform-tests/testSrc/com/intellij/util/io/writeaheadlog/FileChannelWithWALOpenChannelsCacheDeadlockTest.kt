// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.writeaheadlog

import com.intellij.platform.util.io.storages.circular.WriteAheadLogOverCircularBuffer
import com.intellij.util.io.ChannelsAccessor
import com.intellij.util.io.FileChannelInterruptsRetryer
import com.intellij.util.io.OpenChannelsCache
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val WAL_CAPACITY = 64 * 1024

class FileChannelWithWALOpenChannelsCacheDeadlockTest {
  @Test
  fun `WAL flush during cached channel eviction does not block the channels cache`(@TempDir tempDir: Path) {
    val caseDir = tempDir.resolve("wal")
    val storagePath = caseDir.resolve("storage.bin").toAbsolutePath()
    val nextPath = caseDir.resolve("next.bin").toAbsolutePath()
    val probePath = caseDir.resolve("probe.bin").toAbsolutePath()
    Files.createDirectories(caseDir)
    val writeEntered = CountDownLatch(1)
    val writeMayFinish = CountDownLatch(1)
    val noOpBackingAccessor = NoOpChannelsAccessor(readOnly = false)

    val writeAheadLog = WriteAheadLogOverCircularBuffer.openDefaultWAL(
      caseDir.resolve("write-ahead-log.wal"),
      caseDir.resolve("write-ahead-log.paths"),
      WAL_CAPACITY,
    ) { _, _, data ->
      writeEntered.countDown()
      await(writeMayFinish)
      data.position(data.limit())
    }

    val walChannelsCache = OpenChannelsCache("wal-backed-test-cache", 1) { path, readOnly ->
      FileChannelWithWAL(path, writeAheadLog, noOpBackingAccessor, readOnly)
    }
    val walAccessor = walChannelsCache.asWritable()

    val flushFinished = CountDownLatch(1)
    val flushFailure = AtomicReference<Throwable>()
    val evictionFinished = CountDownLatch(1)
    val evictionFailure = AtomicReference<Throwable>()
    val probeFinished = CountDownLatch(1)
    val probeFailure = AtomicReference<Throwable>()

    val flushThread: Thread
    val evictionThread: Thread
    val probeThread: Thread

    try {
      walAccessor.executeOp(storagePath) { channel ->
        channel.write(ByteBuffer.wrap(byteArrayOf(42)), 0)
      }

      flushThread = Thread {
        try {
          writeAheadLog.flush()
        }
        catch (t: Throwable) {
          flushFailure.set(t)
        }
        finally {
          flushFinished.countDown()
        }
      }.also {
        it.name = "test-wal-flush-with-leased-record"
        it.isDaemon = true
        it.start()
      }

      assertTrue(writeEntered.await(5, TimeUnit.SECONDS), "WAL flush must enter ToFileWriter.write and lease the record")

      evictionThread = Thread {
        try {
          walAccessor.executeOp(nextPath) { }
        }
        catch (t: Throwable) {
          evictionFailure.set(t)
        }
        finally {
          evictionFinished.countDown()
        }
      }.also {
        it.name = "test-wal-channel-eviction"
        it.isDaemon = true
        it.start()
      }

      assertEvictionWaitsForWalRecordLease(evictionThread)

      probeThread = Thread {
        try {
          walAccessor.executeOp(probePath) { }
        }
        catch (t: Throwable) {
          probeFailure.set(t)
        }
        finally {
          probeFinished.countDown()
        }
      }.also {
        it.name = "test-wal-cache-probe"
        it.isDaemon = true
        it.start()
      }

      val probeCompletedWhileWalRecordIsLeased = try {
        probeFinished.await(2, TimeUnit.SECONDS)
      }
      finally {
        writeMayFinish.countDown()
        flushThread.join(5_000)
        evictionThread.join(5_000)
        probeThread.join(5_000)
      }

      flushFailure.get()?.let { throw AssertionError("WAL flush failed", it) }
      evictionFailure.get()?.let { throw AssertionError("WAL-backed channel eviction failed", it) }
      probeFailure.get()?.let { throw AssertionError("Cache probe failed", it) }
      assertTrue(flushFinished.count == 0L, "WAL flush must finish after ToFileWriter.write is released")
      assertTrue(evictionFinished.count == 0L, "Eviction must finish after WAL flush releases the record lease")
      assertTrue(
        probeCompletedWhileWalRecordIsLeased,
        "WAL-backed channel eviction must not keep OpenChannelsCache locked while FileChannelWithWAL.close() waits for a WAL record lease"
      )
    }
    finally {
      writeMayFinish.countDown()
      writeAheadLog.close()
    }
  }

  private fun assertEvictionWaitsForWalRecordLease(evictionThread: Thread) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (System.nanoTime() < deadline) {
      if (stackContainsWaitForRecordLease(evictionThread)) {
        return
      }
      Thread.sleep(10)
    }
    assertTrue(
      stackContainsWaitForRecordLease(evictionThread),
      "Eviction must wait for the WAL record lease from FileChannelWithWAL.close()"
    )
  }

  private fun stackContainsWaitForRecordLease(thread: Thread): Boolean {
    return thread.stackTrace.any { frame -> frame.methodName == "waitForRecordLeaseToRelease" }
  }

  private fun await(latch: CountDownLatch) {
    var interrupted = false
    while (true) {
      try {
        if (latch.await(30, TimeUnit.SECONDS)) {
          break
        }
      }
      catch (_: InterruptedException) {
        interrupted = true
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt()
    }
  }

  private class NoOpChannelsAccessor(
    private val readOnly: Boolean,
  ) : ChannelsAccessor {
    override fun isReadOnly(): Boolean = readOnly

    override fun <T> executeOp(path: Path, operation: ChannelsAccessor.FileChannelOperation<T?>): T? {
      throw AssertionError("The test does not expect FileChannelWithWAL to touch the backing channel")
    }

    override fun <T> executeIdempotentOp(path: Path, operation: FileChannelInterruptsRetryer.FileChannelIdempotentOperation<T?>): T? {
      throw AssertionError("The test does not expect FileChannelWithWAL to touch the backing channel")
    }

    override fun closeChannel(path: Path) {
    }
  }
}
