// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.DumbAware

internal class ToggleShowStickyLinesGloballyAction : ToggleAction(), DumbAware {

  override fun isSelected(event: AnActionEvent): Boolean {
    val settings = EditorSettingsExternalizable.getInstance()
    // true -> "don't show"
    return settings.areStickyLinesShown()
  }

  override fun setSelected(event: AnActionEvent, isSelected: Boolean) {
    val settings = EditorSettingsExternalizable.getInstance()
    val shown = settings.areStickyLinesShown()
    if (shown != isSelected) {
      settings.setStickyLinesShown(isSelected)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
