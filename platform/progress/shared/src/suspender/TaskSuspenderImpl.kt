// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress.suspender

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.CoroutineSuspenderElement
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.containers.forEachLoggingErrors
import java.util.concurrent.CopyOnWriteArrayList

class TaskSuspenderImpl internal constructor(@NlsContexts.ProgressText val suspendedText: String) : TaskSuspender {
  private val coroutineSuspenderImpl = CoroutineSuspenderElement(active = true)
  private val listeners = CopyOnWriteArrayList<TaskSuspenderListener>()

  override fun addListener(listener: TaskSuspenderListener) {
    listeners.add(listener)
  }

  override fun isPaused(): Boolean {
    return coroutineSuspenderImpl.isPaused()
  }

  override fun pause(@NlsContexts.ProgressText reason: String?) {
    coroutineSuspenderImpl.pause()
    listeners.forEachLoggingErrors(LOG) { it.onPause(reason) }
  }

  override fun resume() {
    coroutineSuspenderImpl.resume()
    listeners.forEachLoggingErrors(LOG) { it.onResume() }
  }

  companion object {
    private val LOG = logger<TaskSuspenderImpl>()
  }
}