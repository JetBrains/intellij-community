// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ToolWindowEmptyStateAction.rebuildContentUi
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.COMMIT_TOOLWINDOW_ID
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ui.StatusText
import java.util.function.Supplier
import javax.swing.JComponent

private class ChangeViewToolWindowFactory : VcsToolWindowFactory() {
  private val shouldShowWithoutActiveVcs = Registry.get("vcs.empty.toolwindow.show")

  override fun init(window: ToolWindow) {
    super.init(window)

    window.stripeTitleProvider = Supplier {
      window.project.takeIf { !it.isDisposed }?.let { ProjectLevelVcsManager.getInstance(it).allActiveVcss.singleOrNull()?.displayName }
      ?: IdeBundle.message("toolwindow.stripe.Version_Control")
    }

    window.setAdditionalGearActions(ActionManager.getInstance().getAction("LocalChangesView.GearActions") as ActionGroup)
  }

  override fun setEmptyState(project: Project, state: StatusText) {
    setChangesViewEmptyState(state, project)
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    super.createToolWindowContent(project, toolWindow)

    if (toolWindow.contentManager.isEmpty) {
      // to show id label
      rebuildContentUi(toolWindow)
    }
  }

  override fun updateState(toolWindow: ToolWindow) {
    super.updateState(toolWindow)

    if (shouldShowWithoutActiveVcs.asBoolean().not()) {
      toolWindow.isShowStripeButton = showInStripeWithoutActiveVcs(toolWindow.project)
    }
  }

  override fun isAvailable(project: Project) = project.isTrusted()

  private fun showInStripeWithoutActiveVcs(project: Project): Boolean {
    return shouldShowWithoutActiveVcs.asBoolean() || ProjectLevelVcsManager.getInstance(project).hasAnyMappings()
  }
}

private class CommitToolWindowFactory : VcsToolWindowFactory() {
  override fun init(window: ToolWindow) {
    super.init(window)

    window.setAdditionalGearActions(ActionManager.getInstance().getAction("CommitView.GearActions") as ActionGroup)
  }

  override fun setEmptyState(project: Project, state: StatusText) {
    setCommitViewEmptyState(state, project)
  }

  override fun isAvailable(project: Project): Boolean {
    return ProjectLevelVcsManager.getInstance(project).hasAnyMappings() &&
           ChangesViewContentManager.isCommitToolWindowShown(project) &&
           project.isTrusted()
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    super.createToolWindowContent(project, toolWindow)

    hideIdLabelIfNotEmptyState(toolWindow)

    // to show id label
    if (toolWindow.contentManager.isEmpty) {
      rebuildContentUi(toolWindow)
    }
  }
}
