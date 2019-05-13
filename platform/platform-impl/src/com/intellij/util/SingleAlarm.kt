// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState

class SingleAlarm @JvmOverloads constructor(private val task: Runnable,
                                      private val delay: Int,
                                      parentDisposable: Disposable? = null,
                                      threadToUse: ThreadToUse = ThreadToUse.SWING_THREAD,
                                      private val modalityState: ModalityState? = if (threadToUse == ThreadToUse.SWING_THREAD) ModalityState.NON_MODAL else null) : Alarm(threadToUse, parentDisposable) {
  constructor(task: Runnable, delay: Int, modalityState: ModalityState, parentDisposable: Disposable) : this(task, delay = delay,
                                                                                                             parentDisposable = parentDisposable,
                                                                                                             threadToUse = ThreadToUse.SWING_THREAD,
                                                                                                             modalityState = modalityState)

  constructor(task: Runnable, delay: Int, threadToUse: Alarm.ThreadToUse, parentDisposable: Disposable) : this(task,
                                                                                                               delay = delay,
                                                                                                               parentDisposable = parentDisposable,
                                                                                                               threadToUse = threadToUse,
                                                                                                               modalityState = if (threadToUse == ThreadToUse.SWING_THREAD) ModalityState.NON_MODAL else null)

  init {
    if (threadToUse == ThreadToUse.SWING_THREAD && modalityState == null) {
      throw IllegalArgumentException("modalityState must be not null if threadToUse == ThreadToUse.SWING_THREAD")
    }
  }

  @JvmOverloads
  fun request(forceRun: Boolean = false, delay: Int = this@SingleAlarm.delay) {
    if (isEmpty) {
      addRequest(if (forceRun) 0 else delay)
    }
  }

  fun cancel() {
    cancelAllRequests()
  }

  fun cancelAndRequest() {
    if (!isDisposed) {
      cancel()
      addRequest(delay)
    }
  }

  private fun addRequest(delay: Int) {
    _addRequest(task, delay.toLong(), modalityState)
  }
}

fun pooledThreadSingleAlarm(delay: Int, parentDisposable: Disposable = ApplicationManager.getApplication(), task: () -> Unit): SingleAlarm {
  return SingleAlarm(Runnable(task), delay = delay, threadToUse = Alarm.ThreadToUse.POOLED_THREAD, parentDisposable = parentDisposable)
}