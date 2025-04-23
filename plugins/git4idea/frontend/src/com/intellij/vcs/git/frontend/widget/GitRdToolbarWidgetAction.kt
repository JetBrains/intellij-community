// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend.widget

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.ui.RowIcon
import com.intellij.vcs.git.frontend.GitFrontendBundle
import com.intellij.vcs.git.frontend.repo.GitRepositoriesFrontendHolder
import com.intellij.vcs.git.shared.isRdBranchWidgetEnabled
import com.intellij.vcs.git.shared.rpc.GitWidgetState
import icons.DvcsImplIcons

internal class GitRdToolbarWidgetAction : ExpandableComboAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun createPopup(event: AnActionEvent): JBPopup? {
    val project = event.project ?: return null
    val refs = GitRepositoriesFrontendHolder.getInstance(project).getAll().flatMap {
      buildList {
        add(it.repositoryId.rootPath.toString())
        addAll(it.state.refs.localBranches.map { it.fullName })
        addAll(it.state.refs.remoteBranches.map { it.fullName })
      }
    }

    val baseListPopupStep = object : BaseListPopupStep<String>(null, refs) {
      override fun isSpeedSearchEnabled() = true
    }

    return JBPopupFactory.getInstance().createListPopup(baseListPopupStep)
  }

  override fun update(e: AnActionEvent) {
    if (!Registry.isRdBranchWidgetEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    doUpdate(e, project)
  }

  private fun doUpdate(e: AnActionEvent, project: Project) {
    val state = GitWidgetStateHolder.getInstance(project).currentState
    if (state is GitWidgetState.OnRepository) {
      GitWidgetPlaceholder.updatePlaceholder(project, state.presentationData.text)
    }
    e.presentation.putClientProperty(GIT_WIDGET_STATE_KEY, state)

    when (state) {
      GitWidgetState.DoNotShow -> {
        e.presentation.isEnabledAndVisible = false
      }
      GitWidgetState.NoVcs -> updateNoVcs(project, e)
      GitWidgetState.UnknownGitRepository -> updateUnknownGitRepo(project, e)
      is GitWidgetState.OnRepository -> updateOnGitRepo(project, state, e)
    }
  }

  private fun updateNoVcs(project: Project, e: AnActionEvent) {
    val placeholder = GitWidgetPlaceholder.getPlaceholder(project)
    with(e.presentation) {
      isEnabledAndVisible = true
      text = placeholder ?: GitFrontendBundle.message("git.toolbar.widget.no.repo")
      icon = if (placeholder != null) WIDGET_ICON else null
      description = GitFrontendBundle.message("git.toolbar.widget.no.repo.tooltip")
    }
  }

  private fun updateUnknownGitRepo(project: Project, e: AnActionEvent) {
    val placeholder = GitWidgetPlaceholder.getPlaceholder(project)
    with(e.presentation) {
      isEnabledAndVisible = true
      text = placeholder ?: GitFrontendBundle.message("git.toolbar.widget.no.loaded.repo")
      icon = WIDGET_ICON
      description = null
    }
  }

  private fun updateOnGitRepo(
    project: Project,
    state: GitWidgetState.OnRepository,
    e: AnActionEvent,
  ) {
    val presentation = state.presentationData
    with(e.presentation) {
      isEnabledAndVisible = true
      icon = presentation.icon?.icon() ?: AllIcons.General.Vcs
      text = presentation.text
      description = presentation.description
    }

    e.presentation.putClientProperty(ActionUtil.SECONDARY_ICON, getInAndOutIcons(presentation))
  }

  companion object {
    private val GIT_WIDGET_STATE_KEY = Key.create<GitWidgetState>("git-widget-state")
    private val WIDGET_ICON = AllIcons.General.Vcs

    private fun getInAndOutIcons(presentation: GitWidgetState.RepositoryPresentation): RowIcon? {
      val inOutIcons = listOfNotNull(
        DvcsImplIcons.Incoming.takeIf { presentation.syncStatus.incoming },
        DvcsImplIcons.Outgoing.takeIf { presentation.syncStatus.outgoing },
      )
      return if (inOutIcons.isEmpty()) null else RowIcon(*inOutIcons.toTypedArray())
    }
  }
}

private object GitWidgetPlaceholder {
  private const val GIT_WIDGET_PLACEHOLDER_KEY = "git-widget-placeholder"

  fun updatePlaceholder(project: Project, newPlaceholder: @NlsSafe String?) {
    PropertiesComponent.getInstance(project).setValue(GIT_WIDGET_PLACEHOLDER_KEY, newPlaceholder)
  }

  fun getPlaceholder(project: Project): @NlsSafe String? =
    PropertiesComponent.getInstance(project).getValue(GIT_WIDGET_PLACEHOLDER_KEY)
}