// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.widget.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.vcs.git.shared.repo.GitRepositoriesFrontendHolder
import com.intellij.vcs.git.shared.rpc.GitUiSettingsApi
import com.intellij.vcs.git.shared.widget.GitWidgetUpdatesNotifier
import git4idea.GitDisposable
import git4idea.config.GitVcsSettings
import kotlinx.coroutines.launch

internal abstract class GitWidgetSettingsToggleAction(
  private val requireMultiRoot: Boolean = false,
) : ToggleAction(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend, DumbAware {
  final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  final override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return isSelected(project, GitVcsSettings.getInstance(project))
  }

  abstract fun isSelected(project: Project, settings: GitVcsSettings): Boolean

  override fun update(e: AnActionEvent) {
    val enabledAndVisible = e.project != null &&
                            e.getData(GitBranchesWidgetKeys.POPUP) != null

    e.presentation.isEnabledAndVisible = isEnabledAndVisible(e, requireMultiRoot)
    if (enabledAndVisible) {
      super.update(e)
    }
  }

  protected fun changeSetting(
    project: Project,
    refreshWidget: Boolean = true,
    operation: suspend (GitUiSettingsApi) -> Unit,
  ) {
    GitDisposable.getInstance(project).childScope("Git widget settings change").launch {
      operation(GitUiSettingsApi.getInstance())
      if (refreshWidget) {
        GitWidgetUpdatesNotifier.getInstance(project).refresh()
      }
    }
  }

  internal companion object {
    fun isEnabledAndVisible(e: AnActionEvent, requireMultiRoot: Boolean): Boolean {
      val project = e.project ?: return false
      return e.getData(GitBranchesWidgetKeys.POPUP) != null && (!requireMultiRoot || isMultiRoot(project))
    }

    fun isMultiRoot(project: Project): Boolean {
      val repositoriesHolder = GitRepositoriesFrontendHolder.getInstance(project)
      return if (!repositoriesHolder.initialized) false else repositoriesHolder.getAll().size > 1
    }
  }
}
