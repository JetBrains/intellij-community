// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.panel

/**
 * @author Konstantin Bulenkov
 */
class UIFreezeAction : DumbAwareAction("UI Freeze") {
  override fun actionPerformed(e: AnActionEvent) {
    var seconds = 15
    val ui = panel {
      row("Duration in seconds:") {
        intTextField(IntRange(1, 300))
          .bindIntText({seconds}, {seconds = it})
      }
    }

    if (dialog("Set Freeze Duration", ui).showAndGet()) {
      Thread.sleep(seconds * 1_000L)
    }
  }
}