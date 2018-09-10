// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.impl.local.FileWatcher
import com.intellij.util.TimeoutUtil
import java.io.File
import kotlin.test.assertTrue

@Suppress("MemberVisibilityCanBePrivate")
internal object FileWatcherTestUtil {
  internal const val START_STOP_DELAY = 10000L      // time to wait for the watcher spin up/down
  internal const val INTER_RESPONSE_DELAY = 500L    // time to wait for a next event in a sequence
  internal const val NATIVE_PROCESS_DELAY = 60000L  // time to wait for a native watcher response
  internal const val SHORT_PROCESS_DELAY = 5000L    // time to wait when no native watcher response is expected

  internal fun startup(watcher: FileWatcher, notifier: ((String) -> Unit)?) {
    watcher.startup(notifier)
    wait { !watcher.isOperational }
  }

  internal fun shutdown(watcher: FileWatcher) {
    watcher.shutdown()
    wait { watcher.isOperational }
  }

  internal fun watch(watcher: FileWatcher, file: File, recursive: Boolean = true): LocalFileSystem.WatchRequest {
    val request = LocalFileSystem.getInstance().addRootToWatch(file.path, recursive)!!
    wait { watcher.isSettingRoots }
    return request
  }

  internal fun unwatch(watcher: FileWatcher, request: LocalFileSystem.WatchRequest) {
    LocalFileSystem.getInstance().removeWatchedRoot(request)
    wait { watcher.isSettingRoots }
  }

  internal fun wait(timeout: Long = START_STOP_DELAY, condition: () -> Boolean) {
    val stopAt = System.currentTimeMillis() + timeout
    while (condition()) {
      assertTrue(System.currentTimeMillis() < stopAt, "operation timed out")
      TimeoutUtil.sleep(10)
    }
  }
}