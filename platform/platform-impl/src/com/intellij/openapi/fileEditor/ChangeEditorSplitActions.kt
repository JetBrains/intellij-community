// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareToggleAction

class EditorSplitGroup : DefaultActionGroup() {
  override fun update(e: AnActionEvent) {
    val editor = e.getEditorWithPreview()
    e.presentation.isEnabledAndVisible = editor != null
    if (editor != null) {
      e.presentation.icon = TextEditorWithPreview.getEditorWithPreviewIcon(editor.isVerticalSplit)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}

abstract class ChangeEditorSplitAction(private val myVerticalSplit: Boolean) : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun isSelected(e: AnActionEvent): Boolean {
    val editor = e.getEditorWithPreview() ?: return false
    return editor.isVerticalSplit == myVerticalSplit
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      val editor = e.getEditorWithPreview() ?: return
      editor.isVerticalSplit = myVerticalSplit
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}

private fun AnActionEvent.getEditorWithPreview(): TextEditorWithPreview? {
  return getData(PlatformCoreDataKeys.FILE_EDITOR) as? TextEditorWithPreview
}

class SplitVerticallyAction : ChangeEditorSplitAction(false)

class SplitHorizontallyAction : ChangeEditorSplitAction(true)