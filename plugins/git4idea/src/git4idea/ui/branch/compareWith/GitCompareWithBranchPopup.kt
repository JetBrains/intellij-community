// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.compareWith

import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.util.ui.JBDimension
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.popup.GitBranchesTreePopupBase
import git4idea.ui.branch.popup.GitBranchesTreePopupMinimalRenderer
import git4idea.ui.branch.popup.GitBranchesTreePopupStepBase
import git4idea.ui.branch.tree.GitBranchesTreeModel
import git4idea.ui.branch.tree.GitBranchesTreeRenderer
import git4idea.ui.branch.tree.GitBranchesTreeShowTagsAction
import git4idea.ui.branch.tree.GitBranchesTreeSingleRepoModel
import git4idea.ui.branch.tree.createTreePathFor
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.tree.TreePath

class GitCompareWithBranchPopupStep(
  project: Project,
  private val repository: GitRepository,
  private val onRefSelected: Consumer<GitReference>,
) : GitBranchesTreePopupStepBase(project = project, selectedRepository = null, repositories = listOf(repository)) {
  private var finalRunnable: Runnable? = null

  override fun getFinalRunnable() = finalRunnable

  override var treeModel: GitBranchesTreeModel = createTreeModel(false)
    private set

  override fun createTreeModel(filterActive: Boolean): GitBranchesTreeModel {
    return GitCompareWithBranchesTreeModel(project, repository).apply(GitBranchesTreeSingleRepoModel::init)
  }

  override fun setTreeModel(treeModel: GitBranchesTreeModel) {
    this.treeModel = treeModel
  }

  override fun onChosen(selectedValue: Any?, finalChoice: Boolean): PopupStep<*>? {
    val refUnderRepository = selectedValue as? GitBranchesTreeModel.RefUnderRepository
    val reference = selectedValue as? GitReference ?: refUnderRepository?.ref

    if (reference != null) {
      finalRunnable = Runnable { onRefSelected.accept(reference) }
    }

    return PopupStep.FINAL_CHOICE
  }

  override fun getTitle(): String = DvcsBundle.message("popup.title.select.branch.to.compare")
}

private class GitCompareWithBranchesTreeModel(project: Project, repository: GitRepository) : GitBranchesTreeSingleRepoModel(project, repository, emptyList()) {
  override fun getLocalBranches(): Collection<GitLocalBranch> = repository.branches.localBranches.skipCurrentBranch()
  override fun getRecentBranches(): Collection<GitLocalBranch> = super.getRecentBranches().skipCurrentBranch()

  private fun Collection<GitLocalBranch>.skipCurrentBranch(): Collection<GitLocalBranch> {
    val currentBranch = repository.currentBranch
    return this.filter { it != currentBranch }
  }

  override fun getPreferredSelection(): TreePath? {
    return getPreferredBranch()?.let { createTreePathFor(this, it) }
  }
}

class GitCompareWithBranchPopup(
  project: Project,
  step: GitCompareWithBranchPopupStep,
) : GitBranchesTreePopupBase<GitCompareWithBranchPopupStep>(project = project,
                                                            step = step,
                                                            parent = null,
                                                            parentValue = null,
                                                            dimensionServiceKey = DIMENSION_SERVICE_KEY) {
  init {
    minimumSize = JBDimension(350, 400)
  }

  override fun getSearchFiledEmptyText(): String = GitBundle.message(
    "git.compare.with.branch.search.field.empty.text",
    if (GitBranchesTreeShowTagsAction.isSelected(project)) 1 else 0
  )

  override fun getTreeEmptyText(searchPattern: String?): String = GitBundle.message("git.compare.with.branch.search.not.found", searchPattern)

  override fun createRenderer(treeStep: GitCompareWithBranchPopupStep): GitBranchesTreeRenderer =
    GitBranchesTreePopupMinimalRenderer(treeStep)

  override fun getOldUiHeaderComponent(c: JComponent?): JComponent? =
    super.getNewUiHeaderComponent(c)

  override fun getHeaderToolbar(): ActionToolbar {
    val settingsGroup = am.getAction(HEADER_SETTINGS_ACTION_GROUP)
    return am.createActionToolbar(TOP_LEVEL_ACTION_PLACE, DefaultActionGroup(settingsGroup), true)
      .apply {
        targetComponent = content
        setReservePlaceAutoPopupIcon(false)
        component.isOpaque = false
      }
  }

  companion object {
    private const val DIMENSION_SERVICE_KEY = "Git.Compare.With.Branch.Popup"
    private const val HEADER_SETTINGS_ACTION_GROUP = "Git.Compare.With.Branch.Popup.Settings"
  }
}