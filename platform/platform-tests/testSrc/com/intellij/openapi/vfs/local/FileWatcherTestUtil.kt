// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.local

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.impl.local.FileWatcher
import com.intellij.util.TimeoutUtil
import org.junit.Assert.assertTrue
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Suppress("MemberVisibilityCanBePrivate")
internal object FileWatcherTestUtil {
  internal const val START_STOP_DELAY = 10_000L      // time to wait for the watcher setup
  internal const val INTER_RESPONSE_DELAY = 500L     // time to wait for the next event in a sequence
  internal const val NATIVE_PROCESS_DELAY = 60_000L  // time to wait for a native watcher's response
  internal const val SHORT_PROCESS_DELAY = 5_000L    // time to wait when no native watcher's response is expected

  internal fun startup(watcher: FileWatcher, notifier: ((String) -> Unit)?) {
    watcher.startup(notifier)
    wait { !watcher.isOperational }
  }

  internal fun shutdown(watcher: FileWatcher) {
    watcher.shutdown()
    wait { watcher.isOperational }
  }

  internal fun wait(timeout: Long = START_STOP_DELAY, condition: () -> Boolean) {
    val stopAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout)
    while (condition()) {
      assertTrue("operation timed out", System.nanoTime() < stopAt)
      TimeoutUtil.sleep(10)
    }
  }

  internal fun refresh(file: Path): VirtualFile {
    val vFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?: throw IllegalStateException("can't get '${file}' into VFS")
    VfsUtilCore.visitChildrenRecursively(vFile, object : VirtualFileVisitor<Any>() {
      override fun visitFile(file: VirtualFile): Boolean { file.children; return true }
    })
    vFile.refresh(false, true)
    return vFile
  }
}
