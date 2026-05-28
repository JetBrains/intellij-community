// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.addCommit

import com.intellij.dvcs.repo.repositoryId
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.git.branch.popup.GitBranchesPopupActions
import com.intellij.vcs.git.branch.popup.GitBranchesPopupBase
import com.intellij.vcs.git.branch.popup.GitBranchesPopupStepBase
import com.intellij.vcs.git.branch.tree.GitBranchesTreeModel
import com.intellij.vcs.git.branch.tree.GitBranchesTreeRenderer
import com.intellij.vcs.git.branch.tree.GitBranchesTreeSingleRepoModel
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.git.repo.GitRepositoryModel
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.GitRemoteBranch
import git4idea.GitStandardLocalBranch
import git4idea.GitTag
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import javax.swing.JComponent
import javax.swing.JTree

internal fun showRemoteBranchSelectionPopup(
  project: Project,
  repository: GitRepository,
  commits: List<VcsFullCommitDetails>,
  onBranchSelected: (GitRemoteBranch) -> Unit,
) {
  val repositoryModel = GitRepositoriesHolder.getInstance(project).get(repository.repositoryId())
  if (repositoryModel == null) return
  val step = GitRemoteBranchSelectionPopupStep(project, repositoryModel, commits.size, onBranchSelected)
  val popup = GitRemoteBranchSelectionPopup(project, step)
  invokeLater {
    popup.showCenteredInCurrentWindow(project)
  }
}

private class GitRemoteBranchSelectionPopupStep(
  project: Project,
  private val repository: GitRepositoryModel,
  private val commitCount: Int,
  private val onBranchSelected: (GitRemoteBranch) -> Unit,
) : GitBranchesPopupStepBase(project = project, selectedRepository = null, repositories = listOf(repository)) {
  private var finalRunnable: Runnable? = null

  override fun getFinalRunnable() = finalRunnable

  override var treeModel: GitBranchesTreeModel = createTreeModel(false)
    private set

  override fun createTreeModel(filterActive: Boolean): GitBranchesTreeModel {
    return GitRemoteBranchesOnlyTreeModel(project, repository).apply { init() }
  }

  override fun setTreeModel(treeModel: GitBranchesTreeModel) {
    this.treeModel = treeModel
  }

  override fun onChosen(selectedValue: Any?, finalChoice: Boolean): PopupStep<*>? {
    val refUnderRepository = selectedValue as? GitBranchesTreeModel.RefUnderRepository
    val remoteBranch = selectedValue as? GitRemoteBranch ?: refUnderRepository?.ref as? GitRemoteBranch

    if (remoteBranch != null) {
      finalRunnable = Runnable { onBranchSelected(remoteBranch) }
    }

    return PopupStep.FINAL_CHOICE
  }

  override fun getTitle(): String = GitBundle.message("popup.title.select.remote.branch.for.commits", commitCount)
}

private class GitRemoteBranchesOnlyTreeModel(
  project: Project,
  repository: GitRepositoryModel,
) : GitBranchesTreeSingleRepoModel(project, repository, emptyList()) {
  // Return empty collections for local branches, recent branches, and tags
  override fun getLocalBranches(): Collection<GitStandardLocalBranch> = emptyList()
  override fun getRecentBranches(): Collection<GitStandardLocalBranch> = emptyList()
  override fun getTags(): Set<GitTag> = emptySet()

  // Keep remote branches from parent
  override fun getRemoteBranches(): Collection<GitRemoteBranch> = repository.state.remoteBranches
}

private class GitRemoteBranchSelectionPopup(
  project: Project,
  step: GitRemoteBranchSelectionPopupStep,
) : GitBranchesPopupBase<GitRemoteBranchSelectionPopupStep>(
  project = project,
  step = step,
  parent = null,
  parentValue = null,
  dimensionServiceKey = DIMENSION_SERVICE_KEY,
) {
  init {
    minimumSize = JBDimension(350, 400)
  }

  override fun getSearchFiledEmptyText(): String = GitBundle.message("git.add.commit.to.remote.branch.search.empty.text")

  override fun getTreeEmptyText(searchPattern: String?): String =
    GitBundle.message("git.add.commit.to.remote.branch.search.not.found", searchPattern)

  override fun createRenderer(): GitBranchesTreeRenderer = GitBranchesTreeMinimalRenderer(treeStep)

  override fun getOldUiHeaderComponent(c: JComponent?): JComponent? = super.getNewUiHeaderComponent(c)

  override fun getHeaderToolbar(): ActionToolbar {
    val settingsGroup = am.getAction(HEADER_SETTINGS_ACTION_GROUP)
    return am.createActionToolbar(GitBranchesPopupActions.MAIN_POPUP_ACTION_PLACE, DefaultActionGroup(settingsGroup), true)
      .apply {
        targetComponent = content
        setReservePlaceAutoPopupIcon(false)
        component.isOpaque = false
      }
  }

  // Shortcut actions are not supported in this popup
  override fun createShortcutActionDataContext(): DataContext = DataContext.EMPTY_CONTEXT

  companion object {
    private const val DIMENSION_SERVICE_KEY = "Git.AddCommit.To.Remote.Branch.Popup"
    private const val HEADER_SETTINGS_ACTION_GROUP = "Git.Compare.With.Branch.Popup.Settings"
  }
}

private class GitBranchesTreeMinimalRenderer(step: GitBranchesPopupStepBase) :
  GitBranchesTreeRenderer(step, favoriteToggleOnClickSupported = false) {

  override val mainPanel: BorderLayoutPanel =
    JBUI.Panels.simplePanel(mainTextComponent).addToLeft(mainIconComponent).andTransparent()

  override fun configureTreeCellComponent(
    tree: JTree,
    userObject: Any?,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ) {
  }
}
