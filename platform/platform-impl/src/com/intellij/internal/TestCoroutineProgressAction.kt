// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.*
import javax.swing.JComponent

class TestCoroutineProgressAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    object : DialogWrapper(project, false, IdeModalityType.MODELESS) {

      init {
        init()
      }

      private val cs = CoroutineScope(SupervisorJob())

      override fun dispose() {
        cs.cancel()
        super.dispose()
      }

      override fun createCenterPanel(): JComponent = panel {
        row {
          button("Cancellable BG Progress") {
            cs.cancellableBGProgress(project)
          }
          button("Non-Cancellable BG Progress") {
            cs.nonCancellableBGProgress(project)
          }
        }
        row {
          button("Cancellable Modal Progress") {
            cs.modalProgress(project)
          }
          button("Non-Cancellable Modal Progress") {
            cs.nonCancellableModalProgress(project)
          }
        }
      }
    }.show()
  }

  private fun CoroutineScope.cancellableBGProgress(project: Project) {
    launch {
      val taskCancellation = TaskCancellation.cancellable()
        .withButtonText("Cancel Button Text")
        .withTooltipText("Cancel tooltip text")
      withBackgroundProgressIndicator(project, "Cancellable task title", taskCancellation) {
        doStuff()
      }
    }
  }

  private fun CoroutineScope.nonCancellableBGProgress(project: Project) {
    launch {
      withBackgroundProgressIndicator(project, "Non cancellable task title", cancellable = false) {
        doStuff()
      }
    }
  }

  private fun CoroutineScope.modalProgress(project: Project) {
    launch {
      withModalProgressIndicator(project, "Modal progress") {
        doStuff()
      }
    }
  }

  private fun CoroutineScope.nonCancellableModalProgress(project: Project) {
    launch {
      withModalProgressIndicator(
        ModalTaskOwner.project(project),
        "Modal progress",
        TaskCancellation.nonCancellable(),
      ) {
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
