// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.vectorDrawable.preview

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

internal sealed class ComposeResourcePreviewBackgroundAction(
  private val background: ComposeResourcePreviewBackground,
) : DumbAwareToggleAction() {

  override fun isSelected(e: AnActionEvent): Boolean = editor(e)?.previewBackground == background

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (!state) return

    editor(e)?.previewBackground = background
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = editor(e) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  private fun editor(e: AnActionEvent): ComposeResourcePreviewEditor? =
    e.getData(ComposeResourcePreviewEditor.DATA_KEY)

  internal class None : ComposeResourcePreviewBackgroundAction(ComposeResourcePreviewBackground.NONE)
  internal class White : ComposeResourcePreviewBackgroundAction(ComposeResourcePreviewBackground.WHITE)
  internal class Black : ComposeResourcePreviewBackgroundAction(ComposeResourcePreviewBackground.BLACK)
  internal class Checkered : ComposeResourcePreviewBackgroundAction(ComposeResourcePreviewBackground.CHECKERED)
}
