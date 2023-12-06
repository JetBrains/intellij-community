// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Semaphore] which can be configured with [BufferOverflow] strategy.
 */
@Experimental
sealed interface OverflowSemaphore {

  /**
   * Suspends until it's possible to acquire a permit,
   * executes [action] in the current coroutine, and returns its result.
   *
   * If suspended, resumes with [CancellationException] if the current coroutine when cancelled.
   * In [BufferOverflow.DROP_LATEST], resumes with [CancellationException] if it's not possible to acquire a permit.
   * In [BufferOverflow.DROP_OLDEST], may resume with [CancellationException] if the current [action] was cancelled by a newer [action].
   */
  suspend fun <T> withPermit(action: suspend CoroutineScope.() -> T): T
}

/**
 * Creates new [OverflowSemaphore] instance.
 *
 * ### Example
 * Each user click should cancel the handling of the previous one.
 * While it's possible to achieve the same with [MutableSharedFlow][kotlinx.coroutines.flow.MutableSharedFlow]
 * and [collectLatest][kotlinx.coroutines.flow.collectLatest],
 * [OverflowSemaphore] allows to handle the actual completion of each particular request.
 *
 * ```
 * private val semaphore = OverflowSemaphore(
 *   permits = 1,
 *   overflow = BufferOverflow.DROP_OLDEST,
 * )
 *
 * fun userClick() {
 *   cs.launch {
 *     try {
 *       semaphore.withPermit {
 *         handleClick()
 *       }
 *       // request handling was completed successfully
 *     }
 *     catch (ce: CancellationException) {
 *       // request handling was cancelled by another request
 *       // OR the current coroutine was cancelled
 *       throw ce
 *     }
 *     catch (t: Throwable) {
 *       // request handling was completed with exception
 *       LOG.error(t)
 *     }
 *     finally {
 *       // request handling was completed
 *     }
 *   }
 * }
 * ```
 *
 * @param permits how many tasks can be run concurrently.
 * Default is 1.
 * @param overflow what to do when the number of acquired permits is greater or equal to [permits].
 * Default is [BufferOverflow.SUSPEND].
 * - In [BufferOverflow.SUSPEND] mode effectively behaves like a [Semaphore] with [Semaphore.availablePermits] set to [permits].
 * - In [BufferOverflow.DROP_LATEST] mode, the latest [OverflowSemaphore.withPermit] call is "cancelled",
 * i.e. it immediately resumes with [CancellationException].
 * - In [BufferOverflow.DROP_OLDEST] mode, the oldest [OverflowSemaphore.withPermit] call is cancelled before acquiring a permit.
 */
@Experimental
fun OverflowSemaphore(
  permits: Int = 1,
  overflow: BufferOverflow = BufferOverflow.SUSPEND,
): OverflowSemaphore {
  require(permits > 0) {
    "Permits cannot be less than 1: $permits"
  }
  return when (overflow) {
    BufferOverflow.SUSPEND -> {
      SuspendSemaphore(permits)
    }
    BufferOverflow.DROP_OLDEST -> {
      if (permits == 1) {
        DropOldestSingleSemaphore()
      }
      else {
        DropOldestSemaphore(permits)
      }
    }
    BufferOverflow.DROP_LATEST -> {
      DropLatestSemaphore(permits)
    }
  }
}

private class SuspendSemaphore(permits: Int) : OverflowSemaphore {

  private val _semaphore = Semaphore(permits)

  override suspend fun <T> withPermit(action: suspend CoroutineScope.() -> T): T {
    return _semaphore.withPermit {
      coroutineScope {
        action()
      }
    }
  }
}

private class DropLatestSemaphore(permits: Int) : OverflowSemaphore {

  private val _semaphore = Semaphore(permits)

  override suspend fun <T> withPermit(action: suspend CoroutineScope.() -> T): T {
    if (_semaphore.tryAcquire()) {
      return try {
        coroutineScope {
          action()
        }
      }
      finally {
        _semaphore.release()
      }
    }
    throw CancellationException()
  }
}

private abstract class DropOldestSemaphoreBase(permits: Int) : OverflowSemaphore {

  private val _semaphore = Semaphore(permits)

  final override suspend fun <T> withPermit(action: suspend CoroutineScope.() -> T): T = coroutineScope {
    // scope job is cancelled by another `withPermit {}` call
    val job = currentCoroutineContext().job
    val oldestJob = publishCurrentJob(job)
    oldestJob?.cancel() // cancel the oldest job
    try {
      _semaphore.withPermit {
        action()
      }
    }
    finally {
      removeCurrentJob(job)
    }
  }

  protected abstract fun publishCurrentJob(currentJob: Job): Job?

  protected abstract fun removeCurrentJob(currentJob: Job)
}

private class DropOldestSemaphore(private val permits: Int) : DropOldestSemaphoreBase(permits) {

  private val _activeJobs = AtomicReference(persistentSetOf<Job>()) // ordered!

  override fun publishCurrentJob(currentJob: Job): Job? {
    var activeJobs = _activeJobs.get()
    while (true) {
      if (activeJobs.size < permits) {
        val newJobs = activeJobs.add(currentJob)
        val witness = _activeJobs.compareAndExchange(activeJobs, newJobs)
        if (witness === activeJobs) {
          return null
        }
        else {
          activeJobs = witness
        }
      }
      else {
        val oldestJob = activeJobs.first() // rely on ordering
        val newJobs = activeJobs.remove(oldestJob).add(currentJob)
        val witness = _activeJobs.compareAndExchange(activeJobs, newJobs)
        if (witness === activeJobs) {
          return oldestJob
        }
        else {
          activeJobs = witness
        }
      }
    }
  }

  override fun removeCurrentJob(currentJob: Job) {
    var activeJobs = _activeJobs.get()
    while (true) {
      val newJobs = activeJobs.remove(currentJob)
      if (newJobs === activeJobs) {
        return // Job was already removed
      }
      val witness = _activeJobs.compareAndExchange(activeJobs, newJobs)
      if (witness === activeJobs) {
        return // CAX ok
      }
      activeJobs = witness
    }
  }
}

private class DropOldestSingleSemaphore : DropOldestSemaphoreBase(permits = 1) {

  private val _activeJob = AtomicReference<Job>(null)

  override fun publishCurrentJob(currentJob: Job): Job? {
    return _activeJob.getAndSet(currentJob)
  }

  override fun removeCurrentJob(currentJob: Job) {
    _activeJob.compareAndSet(currentJob, null)
  }
}
