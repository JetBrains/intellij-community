// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.branch.DvcsBranchesDivergedBanner
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.platform.project.projectId
import com.intellij.ui.popup.WizardPopup
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.git.shared.ref.GitReferenceName
import com.intellij.vcs.git.shared.repo.GitRepositoriesFrontendHolder
import com.intellij.vcs.git.shared.rpc.GitRepositoryApi
import git4idea.GitReference
import git4idea.actions.branch.GitBranchActionsUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchPopupFetchAction
import git4idea.ui.branch.popup.GitBranchesTreePopupStep.Companion.SINGLE_REPOSITORY_ACTION_PLACE
import git4idea.ui.branch.tree.GitBranchesTreeModel.RefUnderRepository
import git4idea.ui.branch.tree.GitBranchesTreeRenderer
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke

internal class GitBranchesTreePopup(
  project: Project,
  step: GitBranchesTreePopupStep,
  parent: JBPopup? = null,
  parentValue: Any? = null,
) : GitBranchesTreePopupBase<GitBranchesTreePopupStep>(project, step, parent, parentValue, DIMENSION_SERVICE_KEY) {
  init {
    installGeneralShortcutActions()
    if (!isNestedPopup()) {
      warnThatBranchesDivergedIfNeeded()
    }
  }

  override fun getSearchFiledEmptyText(): String {
    val isActionFilterSelected = GitBranchesTreePopupFilterByAction.isSelected(project)
    val isRepositoryFilterSelected = GitBranchesTreePopupFilterByRepository.isSelected(project)
    val separatorKind = if (isActionFilterSelected && isRepositoryFilterSelected) 2 else 1
    val byActions =
      if (isActionFilterSelected) GitBundle.message("git.branches.popup.search.field.actions.empty.text", separatorKind) else ""
    val byRepository =
      if (isRepositoryFilterSelected) GitBundle.message("git.branches.popup.search.field.repositories.empty.text", separatorKind) else ""

    return GitBundle.message("git.branches.popup.search.field.empty.text", byActions, byRepository)
  }

  private fun warnThatBranchesDivergedIfNeeded() {
    if (treeStep.isBranchesDiverged()) {
      setWarning(DvcsBundle.message("branch.popup.warning.branches.have.diverged"))
    }
  }

  override fun getTreeEmptyText(searchPattern: String?): String {
    val filtersActive =
      GitBranchesTreePopupFilterByAction.isSelected(project) || GitBranchesTreePopupFilterByRepository.isSelected(project)

    return if (filtersActive) GitBundle.message("git.branches.popup.tree.no.nodes", searchPattern)
    else GitBundle.message("git.branches.popup.tree.no.branches", searchPattern)
  }

  override fun handleIconClick(userObject: Any?): Boolean {
    toggleFavorite(userObject)
    return true
  }

  private fun toggleFavorite(userObject: Any?) {
    val refUnderRepository = userObject as? RefUnderRepository
    val reference = userObject as? GitReference ?: refUnderRepository?.ref ?: return
    val repositories = refUnderRepository?.repository?.let(::listOf) ?: treeStep.affectedRepositories

    val holder = GitRepositoriesFrontendHolder.getInstance(project)
    val makeFavorite = repositories.any { repository -> !holder.get(repository.rpcId).favoriteRefs.contains(reference) }

    GitRepositoryApi.launchRequest(project) {
      toggleFavorite(project.projectId(),
                     repositories.map { it.rpcId },
                     GitReferenceName(reference.fullName),
                     favorite = makeFavorite)
    }
  }

  private fun installGeneralShortcutActions() {
    registerAction("toggle_favorite", KeyStroke.getKeyStroke("SPACE"), object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        tree.lastSelectedPathComponent?.let(TreeUtil::getUserObject).run(::toggleFavorite)
      }
    })
  }

  override fun getHeaderToolbar(): ActionToolbar {
    val settingsGroup = am.getAction(HEADER_SETTINGS_ACTION_GROUP)
    val toolbarGroup = DefaultActionGroup(GitBranchPopupFetchAction(javaClass), settingsGroup)
    return am.createActionToolbar(TOP_LEVEL_ACTION_PLACE, toolbarGroup, true)
      .apply {
        targetComponent = content
        setReservePlaceAutoPopupIcon(false)
        component.isOpaque = false
      }
  }

  override fun createNextStepPopup(nextStep: PopupStep<*>?, parentValue: Any): WizardPopup =
    if (nextStep is GitBranchesTreePopupStep) GitBranchesTreePopup(project, nextStep, this, parentValue)
    else super.createPopup(this, nextStep, parentValue)

  override fun createRenderer(): GitBranchesTreeRenderer {
    return GitBranchesTreePopupRenderer(treeStep)
  }

  override fun createWarning(text: String): JComponent {
    return DvcsBranchesDivergedBanner.create("reference.VersionControl.Git.SynchronousBranchControl", text)
  }

  override fun getShortcutActionPlace(): String = if (isNestedPopup()) SINGLE_REPOSITORY_ACTION_PLACE else TOP_LEVEL_ACTION_PLACE

  companion object {
    private const val DIMENSION_SERVICE_KEY = "Git.Branch.Popup"
    @Language("devkit-action-id")
    private const val HEADER_SETTINGS_ACTION_GROUP = "Git.Branches.Popup.Settings"

    /**
     * @param selectedRepository - Selected repository:
     * e.g. [git4idea.branch.GitBranchUtil.guessRepositoryForOperation] or [git4idea.branch.GitBranchUtil.guessWidgetRepository]
     */
    @JvmStatic
    fun show(project: Project, selectedRepository: GitRepository?) {
      create(project, selectedRepository).showCenteredInCurrentWindow(project)
    }

    /**
     * @param selectedRepository - Selected repository:
     * e.g. [git4idea.branch.GitBranchUtil.guessRepositoryForOperation] or [git4idea.branch.GitBranchUtil.guessWidgetRepository]
     */
    @JvmStatic
    fun create(project: Project, selectedRepository: GitRepository?): JBPopup {
      return GitBranchesTreePopup(project, createBranchesTreePopupStep(project, selectedRepository))
        .apply { setIsMovable(true) }
    }

    @VisibleForTesting
    internal fun createBranchesTreePopupStep(project: Project, selectedRepository: GitRepository?): GitBranchesTreePopupStep {
      val repositories = DvcsUtil.sortRepositories(GitRepositoryManager.getInstance(project).repositories)
      val selectedRepoIfNeeded = if (GitBranchActionsUtil.userWantsSyncControl(project)) null else selectedRepository
      return GitBranchesTreePopupStep(project, selectedRepoIfNeeded, repositories, true)
    }
  }
}
