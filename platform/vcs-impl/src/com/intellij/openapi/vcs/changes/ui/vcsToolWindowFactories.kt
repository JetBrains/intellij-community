// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ToolWindowEmptyStateAction.rebuildContentUi
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.merge.MergeConflictManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.IconManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import java.util.function.Supplier
import javax.swing.UIManager

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

    (toolWindow as? ToolWindowEx)?.setTabActions(ActionManager.getInstance().getAction("LocalChangesView.TabActions"))
  }

  override fun updateState(toolWindow: ToolWindow) {
    super.updateState(toolWindow)

    if (shouldShowWithoutActiveVcs.asBoolean().not()) {
      toolWindow.isShowStripeButton = showInStripeWithoutActiveVcs(toolWindow.project)
    }
  }

  override fun isAvailable(project: Project) = TrustedProjects.isProjectTrusted(project)

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
           TrustedProjects.isProjectTrusted(project)
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    super.createToolWindowContent(project, toolWindow)

    hideCommitIdLabelIfNotEmptyState(toolWindow)

    // to show id label
    if (toolWindow.contentManager.isEmpty) {
      rebuildContentUi(toolWindow)
    }
  }

  override fun updateState(toolWindow: ToolWindow) {
    super.updateState(toolWindow)

    val project = toolWindow.project
    when {
      !toolWindow.isAvailable || !MergeConflictManager.isNonModalMergeEnabled(project) -> return
      !MergeConflictManager.getInstance(project).isMergeConflict() -> toolWindow.setIcon(AllIcons.Toolwindows.ToolWindowCommit)
      else -> {
        val focusColor = UIManager.getColor("ToolWindow.Button.selectedForeground")
        val originalIcon = toolWindow.icon
        if (originalIcon != null) {
          val badgeColor = JBColor { if (toolWindow.isActive) focusColor else JBUI.CurrentTheme.IconBadge.ERROR }
          val badgeIcon = IconManager.getInstance().withIconBadge(originalIcon, badgeColor)
          toolWindow.setIcon(badgeIcon)
        }
      }
    }
  }
}
