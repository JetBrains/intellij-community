// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

fun <Y : Any> runAndCatchNotNull(errorMessage: String, action: () -> Y?): Y {
  try {
    return action() ?: error("<null> was returned!")
  }
  catch (t: Throwable) {
    throw Error("Failed to $errorMessage. ${t.message}", t)
  }
}

private fun assertInnocentThreadToWait() {
  require(!ApplicationManager.getApplication().isWriteAccessAllowed) { "Must not leak write action" }
  require(!ApplicationManager.getApplication().isWriteIntentLockAcquired) { "Must not run in Write Thread" }
  ApplicationManager.getApplication().assertIsNonDispatchThread()
  ApplicationManager.getApplication().assertReadAccessNotAllowed()
}

suspend fun yieldThroughInvokeLater() {
  assertInnocentThreadToWait()

  runTaskAndLogTime("Later Invocations in EDT") {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      yield()
    }
  }
}

private suspend fun completeJustSubmittedDumbServiceTasks(project: Project) {
  assertInnocentThreadToWait()
  runTaskAndLogTime("Completing just submitted DumbService tasks") {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      DumbService.getInstance(project).completeJustSubmittedTasks()
    }
  }
}

suspend fun yieldAndWaitForDumbModeEnd(project: Project) {
  assertInnocentThreadToWait()
  completeJustSubmittedDumbServiceTasks(project)

  runTaskAndLogTime("Awaiting smart mode") {
    project.waitForSmartMode()
  }

  yieldThroughInvokeLater()
}

