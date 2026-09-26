// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware

internal class RemoveCustomWrapsAtCaretAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val wrapsToRemove = editor.caretModel.allCarets
      .flatMap { editor.customWrapModel.getWrapsAtOffset(it.offset) }
    editor.customWrapModel.runBatchMutation {
      wrapsToRemove.forEach { wrap ->
        removeWrap(wrap)
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = run {
      val editor = e.getData(CommonDataKeys.EDITOR) ?: return@run false
      editor.caretModel.allCarets
        .any { editor.customWrapModel.getWrapsAtOffset(it.offset).isNotEmpty() }
    }
  }
}
