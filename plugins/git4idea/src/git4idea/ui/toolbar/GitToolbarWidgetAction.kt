// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.icons.AllIcons
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.RowIcon
import com.intellij.ui.util.maximumWidth
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.git.shared.isRdBranchWidgetEnabled
import git4idea.GitVcs
import git4idea.branch.GitBranchSyncStatus
import git4idea.branch.GitBranchUtil
import git4idea.config.GitExecutableManager
import git4idea.config.GitVcsSettings
import git4idea.config.GitVersion
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitCurrentBranchPresenter
import git4idea.ui.branch.popup.GitBranchesTreePopup
import git4idea.ui.toolbar.GitToolbarWidgetAction.GitWidgetState
import icons.DvcsImplIcons
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JComponent

private val GIT_WIDGET_STATE_KEY = Key.create<GitWidgetState>("git-widget-state")

private val WIDGET_ICON: Icon = AllIcons.General.Vcs

private const val GIT_WIDGET_PLACEHOLDER_KEY = "git-widget-placeholder"

@ApiStatus.Internal
class GitToolbarWidgetAction : ExpandableComboAction(), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun createPopup(event: AnActionEvent): JBPopup? {
    val project = event.project ?: return null
    val state = event.presentation.getClientProperty(GIT_WIDGET_STATE_KEY)

    if (state is GitWidgetState.Repo) {
      return GitBranchesTreePopup.create(project, state.repository)
    }

    if (state is GitWidgetState.GitVcs) {
      val repo = runWithModalProgressBlocking(project, GitBundle.message("action.Git.Loading.Branches.progress")) {
        project.serviceAsync<VcsRepositoryManager>().ensureUpToDate()
        coroutineToIndicator {
          GitBranchUtil.guessWidgetRepository(project, event.dataContext)
        }
      }
      if (repo != null) {
        return GitBranchesTreePopup.create(project, repo)
      }
    }

    updatePlaceholder(project, null)

    val group = if (TrustedProjects.isProjectTrusted(project)) {
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

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).apply { maximumWidth = Int.MAX_VALUE }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    super.updateCustomComponent(component, presentation)
  }

  override fun update(e: AnActionEvent) {
    if (Registry.isRdBranchWidgetEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val project = e.project

    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val state = getWidgetState(project, DvcsUtil.getSelectedFile(e.dataContext))
    if (state is GitWidgetState.Repo) {
      if (state != e.presentation.getClientProperty(GIT_WIDGET_STATE_KEY)) {
        GitVcsSettings.getInstance(project).setRecentRoot(state.repository.root.path)
      }
    }
    e.presentation.putClientProperty(GIT_WIDGET_STATE_KEY, state)
    var syncStatus: GitBranchSyncStatus? = null

    when (state) {
      GitWidgetState.NotActivated,
      GitWidgetState.NotSupported,
      GitWidgetState.OtherVcs -> {
        e.presentation.isEnabledAndVisible = false
        return
      }
      GitWidgetState.NoVcs -> {
        val placeholder = getPlaceholder(project)
        with(e.presentation) {
          isEnabledAndVisible = true
          text = placeholder ?: GitBundle.message("git.toolbar.widget.no.repo")
          icon = if (placeholder != null) WIDGET_ICON else null
          description = GitBundle.message("git.toolbar.widget.no.repo.tooltip")
        }
      }
      is GitWidgetState.GitVcs -> {
        val placeholder = getPlaceholder(project)
        with(e.presentation) {
          isEnabledAndVisible = true
          text = placeholder ?: GitBundle.message("git.toolbar.widget.no.loaded.repo")
          icon = WIDGET_ICON
          description = null
        }
      }
      is GitWidgetState.Repo -> {
        with(e.presentation) {
          isEnabledAndVisible = true

          val presentation = GitCurrentBranchPresenter.getPresentation(state.repository)
          icon = presentation.icon ?: WIDGET_ICON
          text = presentation.text.also { updatePlaceholder(project, it) }
          description = presentation.description
          syncStatus = presentation.syncStatus
        }
      }
    }

    val rightIcons = mutableListOf<Icon>()
    if (syncStatus?.incoming == true) {
      rightIcons.add(DvcsImplIcons.Incoming)
    }
    if (syncStatus?.outgoing == true) {
      rightIcons.add(DvcsImplIcons.Outgoing)
    }
    e.presentation.putClientProperty(ActionUtil.SECONDARY_ICON, when {
      rightIcons.isNotEmpty() -> RowIcon(*rightIcons.toTypedArray())
      else -> null
    })
  }

  @ApiStatus.Internal
  companion object {
    const val BRANCH_NAME_MAX_LENGTH: Int = 80

    private fun updatePlaceholder(project: Project, newPlaceholder: @NlsSafe String?) {
      PropertiesComponent.getInstance(project).setValue(GIT_WIDGET_PLACEHOLDER_KEY, newPlaceholder)
    }

    private fun getPlaceholder(project: Project): @NlsSafe String? =
      PropertiesComponent.getInstance(project).getValue(GIT_WIDGET_PLACEHOLDER_KEY)

    @RequiresBackgroundThread
    fun getWidgetState(project: Project, selectedFile: VirtualFile?): GitWidgetState {
      val vcsManager = ProjectLevelVcsManager.getInstance(project)
      if (!vcsManager.areVcsesActivated()) return GitWidgetState.NotActivated

      val gitRepository = GitBranchUtil.guessWidgetRepository(project, selectedFile)
      if (gitRepository != null) {
        val gitVersion = GitExecutableManager.getInstance().getVersion(project)
        return if (GitVersion.isUnsupportedWslVersion(gitVersion.type)) GitWidgetState.NotSupported
        else GitWidgetState.Repo(gitRepository)
      }

      val allVcss = vcsManager.allActiveVcss
      when {
        allVcss.isEmpty() -> return GitWidgetState.NoVcs
        allVcss.any { it.keyInstanceMethod == GitVcs.getKey() } -> return GitWidgetState.GitVcs
        else -> return GitWidgetState.OtherVcs
      }
    }
  }

  @ApiStatus.Internal
  sealed class GitWidgetState {
    object NotActivated : GitWidgetState()
    object NotSupported : GitWidgetState()
    object NoVcs : GitWidgetState()
    object OtherVcs : GitWidgetState()
    object GitVcs : GitWidgetState()

    class Repo(val repository: GitRepository) : GitWidgetState()
  }
}
