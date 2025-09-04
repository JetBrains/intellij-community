// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.vcs.VcsBundle

internal class ToggleShowIgnoredAction() : DumbAwareToggleAction() {
  init {
    templatePresentation.setText(VcsBundle.messagePointer("changes.action.show.ignored.text"))
    templatePresentation.setDescription(VcsBundle.messagePointer("changes.action.show.ignored.description"))
    templatePresentation.icon = AllIcons.Actions.ToggleVisibility
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val settings = e.getData(ChangesViewDataKeys.SETTINGS)
    val refresher = e.getData(ChangesViewDataKeys.REFRESHER)
    e.presentation.isEnabledAndVisible = settings != null && refresher != null
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return e.getData(ChangesViewDataKeys.SETTINGS)?.showIgnored ?: false
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.getData(ChangesViewDataKeys.SETTINGS)?.showIgnored = state
    e.getData(ChangesViewDataKeys.REFRESHER)?.run()
  }
}