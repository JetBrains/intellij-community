// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.core

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.panel

internal class UIFreezeAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    var seconds = 15
    val ui = panel {
      row(DevCoreBundle.message("freeze.duration.in.seconds")) {
        intTextField(IntRange(1, 300))
          .bindIntText({ seconds }, { seconds = it })
      }
    }

    if (dialog(DevCoreBundle.message("dialog.title.set.freeze.duration"), ui).showAndGet()) {
      logger<UIFreezeAction>().info("Freeze UI for $seconds seconds")
      Thread.sleep(seconds * 1_000L)
    }
  }
}