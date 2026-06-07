// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage

import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.util.io.blobstorage.ByteBufferWriter
import com.intellij.util.io.writeaheadlog.WriteAheadLog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

class PersistentWriteAheadLogStartupTest {
  @Test
  fun `successful setup returns WAL without invalidating caches`(@TempDir tempDir: Path) {
    val writeAheadLog = TestWriteAheadLog()
    var invalidations = 0

    val result = PersistentWriteAheadLogFactory.setup(
      directory = tempDir,
      maxInitAttempts = 1,
      walCapacityBytes = 42,
      toFileWriter = NO_OP_FILE_WRITER,
      invalidateCaches = { invalidations++ },
      writeAheadLogOpener = PersistentWriteAheadLogOpener {
        bufferPath,
        enumeratorPath,
        walCapacityBytes,
        flusherThreadFactory,
        toFileWriter ->
        assertEquals(
          tempDir.resolve("write-ahead-log.wal"),
          bufferPath,
          "setup should resolve WAL buffer file under directory",
        )
        assertEquals(
          tempDir.resolve("write-ahead-log.paths"),
          enumeratorPath,
          "setup should resolve WAL paths file under directory",
        )
        assertEquals(42, walCapacityBytes, "setup should pass requested WAL capacity to opener")
        assertNull(flusherThreadFactory, "setup should not pass a flusher factory when none was requested")
        assertSame(NO_OP_FILE_WRITER, toFileWriter, "setup should pass the file writer to opener unchanged")
        writeAheadLog
      },
    )

    assertSame(writeAheadLog, result, "setup should return the WAL supplied by opener")
    assertEquals(0, invalidations, "Successful setup must not invalidate index caches")
  }

  @Test
  fun `default opener creates persistent WAL in requested directory`(@TempDir tempDir: Path) {
    var invalidations = 0

    val writeAheadLog = PersistentWriteAheadLogFactory.setup(
      directory = tempDir,
      maxInitAttempts = 1,
      toFileWriter = NO_OP_FILE_WRITER,
      invalidateCaches = { invalidations++ },
    )

    checkNotNull(writeAheadLog) {
      "Default opener should create persistent WAL in the requested directory"
    }.use { wal ->
      assertFalse(wal.hasUnfinished(), "Newly created WAL should not have unfinished records")
    }
    assertEquals(0, invalidations, "Successful default opener setup must not invalidate index caches")
  }

  @Test
  fun `failed open deletes WAL files and invalidates caches and retries`(@TempDir tempDir: Path) {
    val bufferPath = tempDir.resolve("write-ahead-log.wal")
    val enumeratorPath = tempDir.resolve("write-ahead-log.paths")
    val enumeratorMapFilePath = tempDir.resolve("write-ahead-log.paths.hashToId")
    Files.write(bufferPath, byteArrayOf(1))
    Files.write(enumeratorPath, byteArrayOf(3))
    Files.write(enumeratorMapFilePath, byteArrayOf(4))

    val writeAheadLog = TestWriteAheadLog()
    var attempts = 0
    var invalidations = 0

    val loggedAction = withExpectedWalRecoveryLogs {
      PersistentWriteAheadLogFactory.openWithRecoveryPolicy(
        bufferPath = bufferPath,
        enumeratorPath = enumeratorPath,
        maxInitAttempts = 2,
        toFileWriter = NO_OP_FILE_WRITER,
        invalidateCaches = { invalidations++ },
        writeAheadLogOpener = PersistentWriteAheadLogOpener { _, _, _, _, _ ->
          attempts++
          if (attempts == 1) {
            throw IOException("broken WAL")
          }

          assertFalse(Files.exists(bufferPath), "WAL buffer file should be deleted before retry")
          assertFalse(Files.exists(enumeratorPath), "WAL paths-enumerator file should be deleted before retry")
          assertFalse(Files.exists(enumeratorMapFilePath), "WAL paths-enumerator-map file should be deleted before retry")
          writeAheadLog
        },
      )
    }

    assertNull(loggedAction.loggedError, "Recovery warning should not be reported as a test error")
    assertSame(writeAheadLog, loggedAction.result, "openWithRecoveryPolicy should return WAL from successful retry")
    assertEquals(2, attempts, "openWithRecoveryPolicy should retry after first opener failure")
    assertEquals(1, invalidations, "Index caches should be invalidated once after failed open")
  }

  @Test
  fun `openWithRecoveryPolicy throws IOException after all attempts fail`(@TempDir tempDir: Path) {
    val firstFailure = IOException("first")
    val secondFailure = IOException("second")
    val failures = listOf(firstFailure, secondFailure)
    var attempts = 0
    var invalidations = 0

    val loggedAction = withExpectedWalRecoveryLogs {
      assertThrows<IOException> {
        PersistentWriteAheadLogFactory.openWithRecoveryPolicy(
          bufferPath = tempDir.resolve("write-ahead-log.wal"),
          enumeratorPath = tempDir.resolve("write-ahead-log.paths"),
          maxInitAttempts = failures.size,
          toFileWriter = NO_OP_FILE_WRITER,
          invalidateCaches = { invalidations++ },
          writeAheadLogOpener = PersistentWriteAheadLogOpener { _, _, _, _, _ ->
            throw failures[attempts++]
          },
        )
      }
    }
    val error = loggedAction.result

    assertNull(
      loggedAction.loggedError,
      "Exhausted recovery attempts should be returned as IOException, not logged as error here",
    )
    assertSame(firstFailure, error.cause, "Aggregated IOException should keep first opener failure as cause")
    assertEquals(
      listOf(secondFailure),
      error.suppressed.toList(),
      "Aggregated IOException should suppress later opener failures",
    )
    assertEquals(failures.size, attempts, "Opener should be called until maxInitAttempts is exhausted")
    assertEquals(failures.size, invalidations, "Each failed open attempt should invalidate index caches")
  }

  @Test
  fun `setup returns null after all attempts fail`(@TempDir tempDir: Path) {
    var attempts = 0
    var invalidations = 0

    val loggedAction = withExpectedWalRecoveryLogs {
      PersistentWriteAheadLogFactory.setup(
        directory = tempDir,
        maxInitAttempts = 2,
        toFileWriter = NO_OP_FILE_WRITER,
        invalidateCaches = { invalidations++ },
        writeAheadLogOpener = PersistentWriteAheadLogOpener { _, _, _, _, _ ->
          attempts++
          throw IOException("broken WAL")
        },
      )
    }

    assertNull(loggedAction.result, "setup should fail open and return null after all attempts fail")
    assertEquals(
      "Write-ahead log can't be initialized (2 attempts failed)",
      loggedAction.loggedError?.message,
      "setup should log the aggregated recovery failure before disabling persistent WAL",
    )
    assertEquals(2, attempts, "setup should use all configured init attempts before failing open")
    assertEquals(2, invalidations, "setup should invalidate index caches after each failed init attempt")
  }

  private class TestWriteAheadLog : WriteAheadLog {
    override fun openFor(file: Path): WriteAheadLog.PerFileWriter = object : WriteAheadLog.PerFileWriter {
      override fun write(fileOffset: Long, writer: ByteBufferWriter, recordSize: Int) = Unit

      override fun applyUnfinished(offsetInFile: Long, length: Int, targetBuffer: ByteBuffer, offsetInBuffer: Int) = Unit

      override fun hasUnfinished(): Boolean = false

      override fun maxUnfinishedWriteOffset(): Long = -1

      override fun flush(): Int = 0
    }

    override fun hasUnfinished(): Boolean = false

    override fun flush(): Int = 0
    override fun close() = Unit
  }

  private data class LoggedActionResult<T>(val result: T, val loggedError: Throwable?)

  private companion object {
    val NO_OP_FILE_WRITER = WriteAheadLog.ToFileWriter { _, _, _ -> }

    private fun <T> withExpectedWalRecoveryLogs(action: () -> T): LoggedActionResult<T> {
      var result: T
      var loggedError: Throwable? = null
      val token = LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
        override fun processWarn(category: String, message: String, t: Throwable?): Boolean = false

        override fun processError(
          category: String,
          message: String,
          details: Array<String>,
          t: Throwable?,
        ): Set<Action> {
          check(loggedError == null) { "Only one error log is expected" }
          loggedError = checkNotNull(t) { "Expected error log with Throwable" }
          return Action.NONE
        }
      })
      try {
        result = action()
      }
      finally {
        token.finish()
      }

      return LoggedActionResult(result, loggedError)
    }
  }
}
