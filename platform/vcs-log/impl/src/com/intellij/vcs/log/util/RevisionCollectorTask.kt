// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.util

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs a task which collects revisions of type [T] in a separate thread and allows to wait for new revisions.
 *
 * @param T the type of revisions to collect.
 * @property project the project associated with the task.
 * @property mainIndicator the progress indicator for the main task.
 * @property fastTaskIndicator the progress indicator for the fast task or null if the fast task is not supported.
 *
 * @see waitForRevisions
 */
abstract class RevisionCollectorTask<T>(protected val project: Project, private val mainIndicator: ProgressIndicator, private val fastTaskIndicator: ProgressIndicator?) {
  private val future: Future<*>
  private val fastFuture: Future<*>?
  private val _revisions = ConcurrentLinkedQueue<T>()
  private val exception = AtomicReference<VcsException>()
  private val firstRevisionCollected = AtomicBoolean(false)

  @Volatile
  private var lastSnapshotSize = 0

  val isCancelled get() = mainIndicator.isCanceled

  protected val revisionsCount get() = _revisions.size

  init {
    future = AppExecutorUtil.getAppExecutorService().submit {
      ProgressManager.getInstance().runProcess(Runnable {
        try {
          collectRevisions {
            synchronized(firstRevisionCollected) {
              if (!firstRevisionCollected.getAndSet(true)) {
                _revisions.clear()
              }
              _revisions.add(it)
            }
          }
        }
        catch (e: VcsException) {
          exception.set(e)
        }
      }, mainIndicator)
    }
    fastFuture = fastTaskIndicator?.let {
      AppExecutorUtil.getAppExecutorService().submit {
        ProgressManager.getInstance().executeProcessUnderProgress(Runnable {
          try {
            collectRevisionsFast {
              synchronized(firstRevisionCollected) {
                if (firstRevisionCollected.get()) {
                  fastTaskIndicator.cancel()
                  return@collectRevisionsFast
                }
                _revisions.add(it)
              }
            }
          }
          catch (e: VcsException) {
            exception.set(e)
          }
        }, fastTaskIndicator)
      }
    }
  }

  @Throws(VcsException::class)
  abstract fun collectRevisions(consumer: (T) -> Unit)

  @Throws(VcsException::class)
  open fun collectRevisionsFast(consumer: (T) -> Unit) = Unit

  /**
   * Waits for task to complete in a loop.
   * Returns if the task was completed, or new revisions were collected.
   *
   * @param intervalMs the interval to check for new revisions, in milliseconds.
   * @return a Pair containing the list of all collected revisions and a boolean indicating whether the task is done.
   * @throws VcsException if an error occurs during the collection of revisions.
   */
  @Throws(VcsException::class)
  fun waitForRevisions(intervalMs: Long): Pair<List<T>, Boolean> {
    throwOnError()
    while (_revisions.size == lastSnapshotSize) {
      try {
        future.get(intervalMs, TimeUnit.MILLISECONDS)
        ProgressManager.checkCanceled()
        throwOnError()
        return Pair(getRevisionsSnapshot(), true)
      }
      catch (_: TimeoutException) {
        ProgressManager.checkCanceled()
      }
    }
    return Pair(getRevisionsSnapshot(), false)
  }

  private fun getRevisionsSnapshot(): List<T> {
    val list = _revisions.toList()
    lastSnapshotSize = list.size
    return list
  }

  @Throws(VcsException::class)
  private fun throwOnError() {
    if (exception.get() != null) throw VcsException(exception.get())
  }

  fun cancel(wait: Boolean) {
    cancel(wait, mainIndicator, future)
    cancel(wait, fastTaskIndicator, fastFuture)
  }

  private fun cancel(wait: Boolean, indicator: ProgressIndicator?, taskFuture: Future<*>?) {
    indicator?.cancel()
    if (wait && taskFuture != null) {
      try {
        taskFuture.get(20, TimeUnit.MILLISECONDS)
      }
      catch (_: Throwable) {
      }
    }
  }
}