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
      randomDelay()
    }
    progressStep(endFraction = 0.25, "Sequential stage") {
      stage(parallel = false)
    }
    progressStep(endFraction = 0.5) {
      stage(parallel = false)
    }
    progressStep(endFraction = 0.75, "Parallel stage") {
      stage(parallel = true)
    }
    progressStep(endFraction = 1.0) {
      stage(parallel = true)
    }
  }

  private suspend fun stage(parallel: Boolean) {
    indeterminateStep("Prepare parallel $parallel") {
      randomDelay()
    }
    val items = (1..if (parallel) 100 else 5).toList()
    val transformed = progressStep(endFraction = 0.1) {
      items.transformWithProgress(parallel) { item, out ->
        progressStep(endFraction = 1.0, text = "Transforming $item") {
          if (Math.random() < 0.5) {
            out(item)
          }
        }
      }
    }
    val filtered = progressStep(endFraction = 0.2) {
      transformed.filterWithProgress(parallel) { item ->
        progressStep(endFraction = 1.0, text = "Filtering $item") {
          randomDelay()
          item % 2 == 0
        }
      }
    }
    val mapped = progressStep(endFraction = 0.3) {
      filtered.mapWithProgress(parallel) { item ->
        progressStep(endFraction = 1.0, text = "Mapping $item") {
          randomDelay()
          item * 2
        }
      }
    }
    progressStep(endFraction = 1.0) {
      mapped.forEachWithProgress(parallel) { item ->
        handleItem(item)
      }
    }
  }

  private suspend fun handleItem(item: Int) {
    indeterminateStep("Prepare $item") {
      randomDelay()
    }
    progressStep(endFraction = 1.0, "Processing $item") {
      progressStep(endFraction = 0.5, "Processing $item step 1") {
        randomDelay()
      }
      progressStep(endFraction = 1.0, "Processing $item step 2") {
        randomDelay()
      }
    }
  }

  private suspend fun randomDelay() {
    delay(100 + (Math.random() * 1000).toLong())
  }
}
