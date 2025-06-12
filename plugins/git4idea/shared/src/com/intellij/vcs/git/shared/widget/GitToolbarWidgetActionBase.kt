// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.widget

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.RowIcon
import com.intellij.ui.util.maximumWidth
import com.intellij.vcs.git.shared.branch.popup.GitBranchesPopup
import com.intellij.vcs.git.shared.repo.GitRepositoriesHolder
import com.intellij.vcs.git.shared.rpc.GitWidgetState
import git4idea.i18n.GitBundle
import icons.DvcsImplIcons
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * This class should be converted to the regular class and registered as a single branches pop-up action
 * in the frontend module.
 */
@ApiStatus.Internal
abstract class GitToolbarWidgetActionBase : ExpandableComboAction(), DumbAware {
  init {
    templatePresentation.text = GitBundle.message("action.main.toolbar.git.Branches.text")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  final override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    doUpdate(e, project)
  }

  final override fun createPopup(event: AnActionEvent): JBPopup? {
    val state = event.presentation.getClientProperty(GIT_WIDGET_STATE_KEY) ?: return null
    val project = event.project ?: return null

    return when (state) {
      GitWidgetState.DoNotShow -> null
      is GitWidgetState.NoVcs -> {
        GitWidgetPlaceholder.updatePlaceholder(project, null)
        getPopupForRepoSetup(state.trustedProject, event)
      }
      is GitWidgetState.OnRepository -> {
        val repo = GitRepositoriesHolder.getInstance(project).get(state.repository)
        GitBranchesPopup.createDefaultPopup(project, repo)
      }
      GitWidgetState.GitRepositoriesNotLoaded -> awaitInitializationAndGetPopup(project, event)
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).apply { maximumWidth = Int.MAX_VALUE }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    super.updateCustomComponent(component, presentation)
  }

  protected open fun doUpdate(e: AnActionEvent, project: Project) {
    val state = GitWidgetStateHolder.getInstance(project).state.value
    if (state is GitWidgetState.OnRepository) {
      GitWidgetPlaceholder.updatePlaceholder(project, state.presentationData.text)
    }
    e.presentation.putClientProperty(GIT_WIDGET_STATE_KEY, state)

    when (state) {
      GitWidgetState.DoNotShow -> {
        e.presentation.isEnabledAndVisible = false
      }
      is GitWidgetState.NoVcs -> updateNoVcs(project, e)
      GitWidgetState.GitRepositoriesNotLoaded -> updateWithReposNotLoadedGitRepo(project, e)
      is GitWidgetState.OnRepository -> updateOnGitRepo(project, state, e)
    }
  }

  private fun updateNoVcs(project: Project, e: AnActionEvent) {
    val placeholder = GitWidgetPlaceholder.getPlaceholder(project)
    with(e.presentation) {
      isEnabledAndVisible = true
      text = placeholder ?: GitBundle.message("git.toolbar.widget.no.repo")
      icon = if (placeholder != null) WIDGET_ICON else null
      description = GitBundle.message("git.toolbar.widget.no.repo.tooltip")
    }
  }

  private fun updateWithReposNotLoadedGitRepo(project: Project, e: AnActionEvent) {
    val placeholder = GitWidgetPlaceholder.getPlaceholder(project)
    with(e.presentation) {
      isEnabledAndVisible = true
      text = placeholder ?: GitBundle.message("git.toolbar.widget.no.loaded.repo")
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

  private fun awaitInitializationAndGetPopup(project: Project, event: AnActionEvent): JBPopup {
    val repo = runWithModalProgressBlocking(project, GitBundle.message("action.Git.Loading.Branches.progress")) {
      val repositoriesHolder = project.service<GitRepositoriesHolder>()
      repositoriesHolder.awaitInitialization()
      val repository = project.service<GitWidgetStateHolder>().state.value as? GitWidgetState.OnRepository
      repository?.let { repositoriesHolder.get(it.repository) }
    }

    return GitBranchesPopup.createDefaultPopup(project, repo)
  }

  private fun getPopupForRepoSetup(trustedProject: Boolean, event: AnActionEvent): ListPopup {
    val group = if (trustedProject) {
      ActionManager.getInstance().getAction("Vcs.ToolbarWidget.CreateRepository") as ActionGroup
    }
    else {
      @Suppress("DialogTitleCapitalization")
      val separator = Separator(GitBundle.message("action.main.toolbar.git.project.not.trusted.separator.text"))
      val trustProjectAction = ActionManager.getInstance().getAction("ShowTrustProjectDialog")
      DefaultActionGroup(separator, trustProjectAction)
    }
    val place = ActionPlaces.getPopupPlace(ActionPlaces.VCS_TOOLBAR_WIDGET)
    return JBPopupFactory.getInstance()
      .createActionGroupPopup(null, group, event.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true, place)
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