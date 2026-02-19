// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.progress.acquireMaybeCancellable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * Storage can only be opened once per application, so when the log is created on project reopening,
 * we have to be sure that the previous project instance has released the storage
 */
@Service(Service.Level.APP)
internal class VcsLogStorageLocker {
  private val locks = ConcurrentHashMap<String, Semaphore>()

  @RequiresBackgroundThread
  fun acquireLock(logId: String) {
    val mutex = locks.computeIfAbsent(logId) { _ ->
      Semaphore(1, true)
    }
    mutex.acquireMaybeCancellable()
  }

  fun releaseLock(logId: String) {
    // locks will "leak" here, but this is ok, since there won't be too many of them
    locks[logId]?.release()
  }

  companion object {
    fun getInstance(): VcsLogStorageLocker = service()
  }
}