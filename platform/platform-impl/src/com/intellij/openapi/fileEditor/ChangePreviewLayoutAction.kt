// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware

internal abstract class ChangePreviewLayoutAction(
  private val layout: TextEditorWithPreview.Layout
): ToggleAction(layout.getName(), layout.getName(), layout.getIcon(null)), DumbAware, LightEditCompatible, ActionRemoteBehaviorSpecification.Frontend {
  override fun isSelected(event: AnActionEvent): Boolean {
    val editor = event.getEditorWithPreview()
    return editor?.getLayout() == layout
  }

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    val editor = event.getEditorWithPreview() ?: return
    if (state) {
      editor.setLayout(layout)
    }
    else if (layout == TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW) {
      editor.setVerticalSplit(!editor.isVerticalSplit())
    }
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    val editor = event.getEditorWithPreview() ?: return
    event.presentation.icon = layout.getIcon(editor)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  class EditorOnly: ChangePreviewLayoutAction(TextEditorWithPreview.Layout.SHOW_EDITOR)

  class EditorAndPreview: ChangePreviewLayoutAction(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW)

  class PreviewOnly: ChangePreviewLayoutAction(TextEditorWithPreview.Layout.SHOW_PREVIEW)
}
