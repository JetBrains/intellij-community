// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A refresh ends when the [AsyncableFileSystem.fsync] of a queued directory throws.
 */
@TestApplication
class AsyncableFileSystemRefreshTest {
  private val fs = FailingFsyncTempFileSystem()
  private lateinit var dir: NewVirtualFile

  @BeforeEach
  fun createDirectory(): Unit = runBlocking {
    val root = checkNotNull(ManagingFS.getInstance().findRoot("/", fs))
    dir = writeAction {
      root.createChildDirectory(this, "dir").also { it.createChildData(this, "a.txt") } as NewVirtualFile
    }
  }

  @Test
  @Suppress("SuspiciousPackagePrivateAccess")
  fun `a refresh ends when fsync fails`() {
    fs.fsyncFails = true
    val worker = RefreshWorker(listOf(dir), false)
    val scan = Thread({ worker.scan() }, "refresh with a failed fsync").apply { isDaemon = true }

    scan.start()
    scan.join(TimeUnit.SECONDS.toMillis(10))

    val stillRunning = scan.isAlive
    if (stillRunning) {
      worker.cancel()
      scan.interrupt()
    }
    assertThat(fs.fsyncCalls.get()).describedAs("the refresh must call fsync").isPositive()
    assertThat(stillRunning).describedAs("the scan loops forever after the failed fsync").isFalse()
    assertThat(dir.isDirty).describedAs("the next refresh must try the directory again").isTrue()
  }
}

private class FailingFsyncTempFileSystem : TempFileSystem(), AsyncableFileSystem {
  private val protocol = "failing-fsync-${System.identityHashCode(this)}"

  @Volatile
  var fsyncFails: Boolean = false

  val fsyncCalls = AtomicInteger()

  override fun getProtocol(): String = protocol

  override fun hasUnfinishedTasks(): Boolean = false

  override fun hasUnfinishedTasksFor(file: VirtualFile): Boolean = false

  override fun fsync() {}

  override fun fsync(file: VirtualFile) {
    fsyncCalls.incrementAndGet()
    if (fsyncFails) throw IOException("fsync failed for ${file.path}")
  }
}
