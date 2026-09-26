// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.plus
import kotlin.concurrent.withLock
import kotlin.coroutines.CoroutineContext


/**
 * The workspace-model-updating methods need to be protected by a reentrant lock.
 *
 * Reentrancy in IntelliJ Platform has extended semantics: it is attributed to a _computation_, not to a thread.
 * It means that a computation can move itself to other threads (with the help of [com.intellij.util.concurrency.TransferredWriteActionService], for example),
 * and it still should know about decisions taken up the asynchronous stack.
 * This can be achieved with the help of thread context, which is attached to a computation rather than a thread.
 *
 * Problematic scenario:
 * ```kotlin
 * // edt
 * runWriteAction {
 *   workspaceModel.replace {
 *     transferWriteActionToBackground {
 *       workspaceModel.update {
 *        // this is bgt, but reentrancy should hold here
 *       }
 *     }
 *   }
 * }
 * ```
 */
@ApiStatus.Internal
class ThreadContextAwareReentrantLock {
  private class ReentrancyCheckMarker(val list: List<ThreadContextAwareReentrantLock>) : CoroutineContext.Element {
    object Key: CoroutineContext.Key<ReentrancyCheckMarker>
    override val key: CoroutineContext.Key<*> = Key
  }

  private val mutex: ReentrantLock = ReentrantLock()

  fun <T> withLock(action: () -> T): T {
    val currentMarker = currentThreadContext()[ReentrancyCheckMarker.Key]
    return if (currentMarker != null && currentMarker.list.contains(this)) {
      action()
    } else {
      val newMarker = ReentrancyCheckMarker((currentMarker?.list ?: emptyList()) + this)
      installThreadContext(currentThreadContext() + newMarker, true) {
        mutex.withLock(action)
      }
    }
  }
}