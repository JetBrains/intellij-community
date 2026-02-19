// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.configurationStore.saveSettingsForRemoteDevelopment
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.platform.vcs.impl.shared.commit.CommitToolWindowViewModel
import com.intellij.util.application

internal sealed class ShowOnDoubleClickToggleAction(private val isEditorPreview: Boolean) :
  DumbAwareToggleAction(),
  ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun update(e: AnActionEvent) {
    val enabled = e.project?.serviceIfCreated<CommitToolWindowViewModel>() != null
    e.presentation.isEnabledAndVisible = enabled
    if (enabled) {
      super.update(e)
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val isCommitToolwindowShown = e.project?.let(::isCommitToolWindowShown) == true
    if (isCommitToolwindowShown) {
      return VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK == isEditorPreview
    }
    else {
      return VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK == isEditorPreview
    }
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    // Make the action behave as "Option" in "ShowOnDoubleClick" group.
    val newState = if (e.isFromContextMenu || state) isEditorPreview else !isEditorPreview

    val isCommitToolwindowShown = e.project?.let(::isCommitToolWindowShown) == true
    if (isCommitToolwindowShown) {
      VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK = newState
    }
    else {
      VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK = newState
    }
    saveSettingsForRemoteDevelopment(e.coroutineScope, application)
  }

  private fun isCommitToolWindowShown(project: Project): Boolean =
    project.service<CommitToolWindowViewModel>().commitTwEnabled.value

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  class EditorPreview : ShowOnDoubleClickToggleAction(true)
  class Source : ShowOnDoubleClickToggleAction(false)
}

