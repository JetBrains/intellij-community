// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.core

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@Suppress("HardCodedStringLiteral")
internal class FrequentBackgroundReadAction: CheckboxAction() {
  private object Manager {
    @Volatile
    var runningJob: Job? = null

    @Volatile
    var sleepDurationMs: Long = 10
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return Manager.runningJob?.isActive == true
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      // Show input dialog to get sleep duration
      val input = Messages.showInputDialog(
        e.project,
        "Enter sleep duration in milliseconds:",
        "Frequent Background Read Action",
        null,
        Manager.sleepDurationMs.toString(),
        null
      )

      if (input.isNullOrBlank()) {
        return // User cancelled
      }

      val duration = input.toLongOrNull()
      if (duration == null || duration < 0) {
        Messages.showErrorDialog(
          e.project,
          "Invalid duration. Please enter a positive number.",
          "Invalid Input"
        )
        return
      }

      Manager.sleepDurationMs = duration

      // Toggle ON: Start repeated background read actions
      Manager.runningJob = e.coroutineScope.launch(Dispatchers.Default) {
        try {
          while (isActive) {
            readActionBlocking {
              Thread.sleep(Manager.sleepDurationMs)
            }
            delay(1.milliseconds)
          }
        } catch (e: CancellationException) {
          // Expected on toggle OFF
          throw e
        }
      }
    } else {
      // Toggle OFF: Cancel the running job
      Manager.runningJob?.cancel()
      Manager.runningJob = null
    }
  }
}
