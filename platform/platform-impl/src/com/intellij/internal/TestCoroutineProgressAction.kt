// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.*
import javax.swing.JComponent

internal class TestCoroutineProgressAction : AnAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    try {
      runBlockingModal(ModalTaskOwner.guess(), "Synchronous never-ending modal progress") {
        awaitCancellation()
      }
    }
    catch (ignored: CancellationException) {

    }

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
        row {
          button("Cancellable Synchronous Modal Progress") {
            runBlockingModal(project, "Cancellable synchronous modal progress") {
              doStuff()
            }
          }
          button("Non-Cancellable Synchronous Modal Progress") {
            runBlockingModal(
              ModalTaskOwner.project(project),
              "Non-cancellable synchronous modal progress",
              TaskCancellation.nonCancellable(),
            ) {
              doStuff()
            }
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
      withBackgroundProgress(project, "Cancellable task title", taskCancellation) {
        doStuff()
      }
    }
  }

  private fun CoroutineScope.nonCancellableBGProgress(project: Project) {
    launch {
      withBackgroundProgress(project, "Non cancellable task title", cancellable = false) {
        doStuff()
      }
    }
  }

  private fun CoroutineScope.modalProgress(project: Project) {
    launch {
      withModalProgress(project, "Modal progress") {
        doStuff()
      }
    }
  }

  private fun CoroutineScope.nonCancellableModalProgress(project: Project) {
    launch {
      withModalProgress(
        ModalTaskOwner.project(project),
        "Modal progress",
        TaskCancellation.nonCancellable(),
      ) {
        doStuff()
      }
    }
  }

  private suspend fun doStuff() {
    indeterminateStep("Indeterminate stage") {
      delay(1000)
    }
    progressStep(endFraction = 0.25) {
      sequentialStage()
    }
    progressStep(endFraction = 0.5, "Sequential stage") {
      sequentialStage()
    }
    progressStep(endFraction = 0.75, "Parallel stage") {
      parallelStage()
    }
    progressStep(endFraction = 1.0) {
      parallelStage()
    }
  }

  private suspend fun sequentialStage() {
    indeterminateStep(text = "Preparing sequential stage") {
      delay(500)
    }
    val times = 10
    val itemDuration = 1.0 / times
    repeat(times) { index ->
      indeterminateStep("Preparing $index") {
        delay(300)
      }
      val endFraction = itemDuration * (index + 1)
      progressStep(endFraction = endFraction, "Processing $index") {
        delay((1000.toDouble() * (times - index) / times).toLong())
      }
    }
  }

  private suspend fun parallelStage() {
    indeterminateStep("Prepare counted stage") {
      delay(1000)
    }
    (1..100).toList().mapParallelWithProgress { item ->
      indeterminateStep("Prepare $item") {
        delay(500)
      }
      progressStep(endFraction = 1.0, "Processing $item") {
        delay(300 + (Math.random() * 1000).toLong())
      }
    }
  }
}
