// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow.splitApi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@ApiStatus.Internal
class ProblemLifetime(val coroutineScope: CoroutineScope) {
  private val idsLock = ReentrantLock()
  private val ids = HashSet<String>()
  private val completionHandlerRegistered = AtomicBoolean()

  fun ensureCompletionHandlerRegistered(completionHandler: () -> Unit) {
    if (completionHandlerRegistered.compareAndSet(false, true)) {
      coroutineScope.coroutineContext.job.invokeOnCompletion { completionHandler() }
    }
  }

  fun bindId(id: String): Boolean = idsLock.withLock {
    if (!isActive()) return@withLock false
    ids.add(id)
    true
  }

  fun unbindId(id: String): Boolean = idsLock.withLock {
    ids.remove(id)
  }

  fun unbindAllIds(): List<String> = idsLock.withLock {
    val result = ids.toList()
    ids.clear()
    result
  }

  fun isActive(): Boolean = coroutineScope.isActive

  override fun toString(): String = coroutineScope.toString()
}
