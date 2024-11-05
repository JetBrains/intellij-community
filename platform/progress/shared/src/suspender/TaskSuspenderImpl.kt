// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress.suspender

import com.intellij.openapi.progress.CoroutineSuspenderElement
import com.intellij.openapi.util.NlsContexts

class TaskSuspenderImpl internal constructor(@NlsContexts.ProgressText val suspendedText: String) : TaskSuspender {
  private val coroutineSuspenderImpl = CoroutineSuspenderElement(active = true)

  override fun isPaused(): Boolean {
    return coroutineSuspenderImpl.isPaused()
  }

  override fun pause(@NlsContexts.ProgressText reason: String?) {
    coroutineSuspenderImpl.pause()
  }

  override fun resume() {
    coroutineSuspenderImpl.resume()
  }
}