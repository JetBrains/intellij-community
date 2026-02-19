// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.compareWith

import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
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
import com.intellij.vcs.git.branch.tree.createTreePathFor
import com.intellij.vcs.git.repo.GitRepositoryModel
import git4idea.GitReference
import git4idea.GitStandardLocalBranch
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.TreePath

internal class GitCompareWithBranchPopupStep(
  project: Project,
  private val repository: GitRepositoryModel,
  private val onRefSelected: Consumer<GitReference>,
) : GitBranchesPopupStepBase(project = project, selectedRepository = null, repositories = listOf(repository)) {
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

private class GitCompareWithBranchesTreeModel(project: Project, repository: GitRepositoryModel) : GitBranchesTreeSingleRepoModel(project, repository, emptyList()) {
  override fun getLocalBranches(): Collection<GitStandardLocalBranch> = repository.state.localBranches.skipCurrentBranch()
  override fun getRecentBranches(): Collection<GitStandardLocalBranch> = super.getRecentBranches().skipCurrentBranch()

  private fun Collection<GitStandardLocalBranch>.skipCurrentBranch(): Collection<GitStandardLocalBranch> {
    val currentBranch = repository.state.currentBranch ?: return this
    return filter { it != currentBranch }
  }

  override fun getPreferredSelection(): TreePath? {
    return getPreferredBranch()?.let { createTreePathFor(this, it) }
  }
}

internal class GitCompareWithBranchPopup(
  project: Project,
  step: GitCompareWithBranchPopupStep,
) : GitBranchesPopupBase<GitCompareWithBranchPopupStep>(project = project,
                                                        step = step,
                                                        parent = null,
                                                        parentValue = null,
                                                        dimensionServiceKey = DIMENSION_SERVICE_KEY) {
  init {
    minimumSize = JBDimension(350, 400)
  }

  override fun getSearchFiledEmptyText(): String = GitBundle.message(
    "git.compare.with.branch.search.field.empty.text",
    if (GitVcsSettings.getInstance(project).showTags()) 1 else 0
  )

  override fun getTreeEmptyText(searchPattern: String?): String = GitBundle.message("git.compare.with.branch.search.not.found", searchPattern)

  override fun createRenderer(): GitBranchesTreeRenderer =
    GitBranchesTreeMinimalRenderer(treeStep)

  override fun getOldUiHeaderComponent(c: JComponent?): JComponent? =
    super.getNewUiHeaderComponent(c)

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
    private const val DIMENSION_SERVICE_KEY = "Git.Compare.With.Branch.Popup"
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