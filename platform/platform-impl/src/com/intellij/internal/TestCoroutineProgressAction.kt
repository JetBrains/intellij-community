// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.progressSink
import com.intellij.openapi.progress.withBackgroundProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

class TestCoroutineProgressAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val scope = CoroutineScope(EmptyCoroutineContext)
    scope.launch {
      try {
        val taskCancellation = TaskCancellation.cancellable()
          .withButtonText("Cancel Button Text")
          .withTooltipText("Cancel tooltip text")
        withBackgroundProgressIndicator(project, "Cancellable task title", taskCancellation) {
          doStuff()
        }
      }
      catch (e: Throwable) {
        throw e
      }
    }
    scope.launch {
      withBackgroundProgressIndicator(project, "Non cancellable task title", cancellable = false) {
        doStuff()
      }
    }
  }

  private suspend fun doStuff() {
    val sink = progressSink()
    val total = 100
    sink?.text("Indeterminate stage")
    delay(2000)
    repeat(total) { iteration ->
      delay(30)
      sink?.fraction(iteration.toDouble() / total)
      sink?.text("progress text $iteration")
      sink?.details("progress details $iteration")
    }
  }
}
