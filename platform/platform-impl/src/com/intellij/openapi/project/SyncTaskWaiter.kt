// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SyncTaskWaiter(private val project: Project,
                     private val title: @NlsContexts.ProgressTitle String,
                     private val isRunning: Flow<Boolean>) {
  fun waitUntilFinished() {
    if (ApplicationManager.getApplication().isWriteAccessAllowed) {
      waitAfterWriteAction()
    }
    else {
      waitNow()
    }
  }

  private fun waitAfterWriteAction() {
    val listenerDisposable = Disposer.newDisposable();
    ApplicationManager.getApplication().addApplicationListener(object : ApplicationListener {
      override fun afterWriteActionFinished(action: Any) {
        try {
          waitNow();
        }
        finally {
          Disposer.dispose(listenerDisposable);
        }
      }
    }, listenerDisposable);
  }

  private fun waitNow() {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runWithModalProgressBlocking(project, title) {
        isRunning.first { !it }
      }
    }
    else {
      runBlockingMaybeCancellable {
        isRunning.first { !it }
      }
    }
  }
}