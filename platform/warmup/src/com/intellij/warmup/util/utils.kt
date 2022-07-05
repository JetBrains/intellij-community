// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.openapi.application.*
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.swing.SwingUtilities
import kotlin.coroutines.resume

fun <Y : Any> runAndCatchNotNull(errorMessage: String, action: () -> Y?): Y {
  try {
    return action() ?: error("<null> was returned!")
  }
  catch (t: Throwable) {
    throw Error("Failed to $errorMessage. ${t.message}", t)
  }
}

private fun assertInnocentThreadToWait() {
  require(!ApplicationManager.getApplication().isReadAccessAllowed) { "Must not leak read action" }
  require(!ApplicationManager.getApplication().isWriteAccessAllowed) { "Must not leak write action" }
  require(!ApplicationManager.getApplication().isWriteThread) { "Must not run in Write Thread" }
  require(!ApplicationManager.getApplication().isDispatchThread) { "Must not run in Dispatch Thread" }
}

suspend fun yieldThroughInvokeLater() {
  assertInnocentThreadToWait()

  runTaskAndLogTime("Later Invocations in EDT") {
    //we use an updated version of UIUtil::dispatchPendingFlushes that works from a non-EDT thread
    check(!SwingUtilities.isEventDispatchThread()) { "Must not call from EDT" }
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      // just wait
    }
  }
}

suspend fun completeJustSubmittedDumbServiceTasks(project: Project) {
  assertInnocentThreadToWait()
  runTaskAndLogTime("Completing just submitted DumbService tasks") {
    invokeAndWaitIfNeeded {
      DumbService.getInstance(project).completeJustSubmittedTasks()
    }
  }
}

suspend fun yieldAndWaitForDumbModeEnd(project: Project) {
  assertInnocentThreadToWait()
  completeJustSubmittedDumbServiceTasks(project)

  runTaskAndLogTime("Awaiting smart mode") {
    suspendCancellableCoroutine<Unit> { cont ->
      DumbService.getInstance(project).runWhenSmart {
        cont.resume(Unit)
      }
    }
  }

  yieldThroughInvokeLater()
}

