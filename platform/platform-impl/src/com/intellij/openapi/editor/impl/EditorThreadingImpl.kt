// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.EditorThreading
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EditorThreadingImpl : EditorThreading {

  override fun doAssertInteractionAllowed() {
    if (!EDT.isCurrentThreadEdt() && !application.isReadAccessAllowed) {
      throw IllegalStateException("Access to Editor models (caret, selection, etc.) is allowed either from EDT, or under read action. Current thread: ${Thread.currentThread()}")
    }
  }

  override fun <T, E : Throwable> doCompute(action: ThrowableComputable<T, E>): T {
    return if (EDT.isCurrentThreadEdt()) {
      action.compute()
    }
    else {
      application.runReadAction(action)
    }
  }

  override fun doRun(action: Runnable) {
    return if (EDT.isCurrentThreadEdt()) {
      action.run()
    }
    else {
      application.runReadAction(action)
    }
  }
}
