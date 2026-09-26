// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.core

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

internal class PooledFreezeAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    var seconds = 15
    var ioDispatcher = false
    val ui = panel {
      row(DevCoreBundle.message("freeze.duration.in.seconds")) {
        intTextField(IntRange(1, 100000))
          .bindIntText({ seconds }, { seconds = it })
      }
      buttonsGroup(DevCoreBundle.message("label.freeze.dispatcher")) {
        row {
          radioButton("Dispatchers.Default", false)
          radioButton("Dispatchers.IO", true)
        }
      }.bind({ ioDispatcher }, { ioDispatcher = it })
    }

    if (dialog(DevCoreBundle.message("dialog.title.set.freeze.duration"), ui).showAndGet()) {
      val freezeScope = e.coroutineScope.childScope("FreezeScope")

      val dispatcher = when {
        ioDispatcher -> Dispatchers.IO
        else -> Dispatchers.Default
      }
      logger<UIFreezeAction>().info("Freeze pooled thread for $seconds seconds on $dispatcher")

      repeat(10000) { // arbitrary big number bigger than any thread pool
        freezeScope.launch(dispatcher) {
          repeat(seconds * 10) {
            ensureActive()
            Thread.sleep(100L)
          }
        }
      }

      @Suppress("OPT_IN_USAGE")
      freezeScope.launch(blockingDispatcher) {
        delay(seconds.seconds)
        logger<UIFreezeAction>().info("Stop freeze")
        freezeScope.cancel()
      }
    }
  }
}
