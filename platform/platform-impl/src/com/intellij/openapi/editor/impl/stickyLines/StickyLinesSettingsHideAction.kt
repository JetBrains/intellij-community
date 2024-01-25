// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.DumbAware

// don't show action
internal class StickyLinesSettingsHideAction : ToggleAction(), DumbAware {

  override fun isSelected(event: AnActionEvent): Boolean {
    val settings = EditorSettingsExternalizable.getInstance()
    // true -> "don't show"
    return !settings.areStickyLinesShown()
  }

  override fun setSelected(event: AnActionEvent, hide: Boolean) {
    val settings = EditorSettingsExternalizable.getInstance()
    val shown = settings.areStickyLinesShown()
    if (shown == hide) {
      settings.setStickyLinesShown(!shown)
      UISettings.getInstance().fireUISettingsChanged()
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
