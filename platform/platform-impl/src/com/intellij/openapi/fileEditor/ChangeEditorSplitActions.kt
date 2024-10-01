// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareToggleAction
import org.jetbrains.annotations.ApiStatus

private class EditorSplitGroup : DefaultActionGroup() {
  override fun update(e: AnActionEvent) {
    val editor = e.getEditorWithPreview()
    e.presentation.isEnabledAndVisible = editor != null
    if (editor != null) {
      e.presentation.icon = TextEditorWithPreview.getEditorWithPreviewIcon(editor.isVerticalSplit())
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

@ApiStatus.Internal
abstract class ChangeEditorSplitAction(private val myVerticalSplit: Boolean) : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun isSelected(e: AnActionEvent): Boolean {
    val editor = e.getEditorWithPreview() ?: return false
    return editor.isVerticalSplit() == myVerticalSplit
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      val editor = e.getEditorWithPreview() ?: return
      editor.setVerticalSplit(myVerticalSplit)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal fun AnActionEvent.getEditorWithPreview(): TextEditorWithPreview? {
  return TextEditorWithPreview.getParentSplitEditor(getData(PlatformCoreDataKeys.FILE_EDITOR))
}

private class SplitVerticallyAction : ChangeEditorSplitAction(false)

private class SplitHorizontallyAction : ChangeEditorSplitAction(true)