// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware

internal class AddCustomWrapAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val carets = editor.caretModel.allCarets
    if (carets.isEmpty()) return
    editor.customWrapModel.runBatchMutation {
      carets.forEach { caret ->
        addWrap(caret.offset)
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
  }
}
