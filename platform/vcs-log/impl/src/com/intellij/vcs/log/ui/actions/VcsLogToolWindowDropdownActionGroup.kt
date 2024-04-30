// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.vcs.log.impl.VcsProjectLog
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class VcsLogToolWindowDropdownActionGroup : DefaultActionGroup(), DumbAware {
  override fun update(e: AnActionEvent) {
    e.presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)

    val project = e.project
    if (project == null || VcsProjectLog.getInstance(project).logManager == null) {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}