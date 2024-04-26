// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable

internal class StickyLinesSettingsDisableAction : AnAction() {

  override fun update(e: AnActionEvent) {
    val settings = EditorSettingsExternalizable.getInstance()
    e.presentation.isEnabledAndVisible = settings.areStickyLinesShown()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val settings = EditorSettingsExternalizable.getInstance()
    if (settings.areStickyLinesShown()) {
      settings.setStickyLinesShown(false)
      UISettings.getInstance().fireUISettingsChanged()
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun isDumbAware(): Boolean {
    return true
  }
}
