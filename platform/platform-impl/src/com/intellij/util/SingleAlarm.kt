// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState

class SingleAlarm @JvmOverloads constructor(private val task: Runnable,
                                            private val delay: Int,
                                            parentDisposable: Disposable? = null,
                                            threadToUse: ThreadToUse = ThreadToUse.SWING_THREAD,
                                            private val modalityState: ModalityState? = computeDefaultModality(threadToUse)) : Alarm(threadToUse, parentDisposable) {
  constructor(task: Runnable, delay: Int, modalityState: ModalityState, parentDisposable: Disposable) : this(task, delay = delay,
                                                                                                             parentDisposable = parentDisposable,
                                                                                                             threadToUse = ThreadToUse.SWING_THREAD,
                                                                                                             modalityState = modalityState)

  constructor(task: Runnable, delay: Int, threadToUse: ThreadToUse, parentDisposable: Disposable) : this(task,
                                                                                                         delay = delay,
                                                                                                         parentDisposable = parentDisposable,
                                                                                                         threadToUse = threadToUse,
                                                                                                         modalityState = computeDefaultModality(
                                                                                                           threadToUse))

  init {
    if (threadToUse == ThreadToUse.SWING_THREAD && modalityState == null) {
      throw IllegalArgumentException("modalityState must be not null if threadToUse == ThreadToUse.SWING_THREAD")
    }
  }

  @JvmOverloads
  fun request(forceRun: Boolean = false, delay: Int = this@SingleAlarm.delay) {
    if (isEmpty) {
      _addRequest(task, if (forceRun) 0 else delay.toLong(), modalityState)
    }
  }

  /**
   * Cancel doesn't interrupt already running task.
   */
  fun cancel() {
    cancelAllRequests()
  }

  /**
   * Cancel doesn't interrupt already running task.
   */
  @JvmOverloads
  fun cancelAndRequest(forceRun: Boolean = false) {
    if (!isDisposed) {
      cancelAllAndAddRequest(task, if (forceRun) 0 else delay, modalityState)
    }
  }

  fun getUnfinishedRequest(): Runnable? {
    val unfinishedTasks = unfinishedRequests
    if (unfinishedTasks.isEmpty()) {
      return null
    }

    LOG.assertTrue(unfinishedTasks.size == 1)
    return unfinishedTasks.first()
  }
}

fun pooledThreadSingleAlarm(delay: Int, parentDisposable: Disposable = ApplicationManager.getApplication(), task: () -> Unit): SingleAlarm {
  return SingleAlarm(Runnable(task), delay = delay, threadToUse = Alarm.ThreadToUse.POOLED_THREAD, parentDisposable = parentDisposable)
}

private fun computeDefaultModality(threadToUse: Alarm.ThreadToUse): ModalityState? {
  return when (threadToUse) {
    Alarm.ThreadToUse.SWING_THREAD -> ModalityState.NON_MODAL
    else -> null
  }
}