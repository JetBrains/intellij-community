// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

internal abstract class StickyLinesAbstractAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  protected fun showStickyLinesSettingsDialog(project: Project?) {
    ShowSettingsUtilImpl.showSettingsDialog(
      project,
      "editor.preferences.appearance",
      ApplicationBundle.message("checkbox.show.sticky.lines")
    )
  }
}
