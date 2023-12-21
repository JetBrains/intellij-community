// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.diverged
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.PopupStep.FINAL_CHOICE
import com.intellij.openapi.ui.popup.SpeedSearchFilter
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.containers.FList
import git4idea.GitBranch
import git4idea.GitVcs
import git4idea.actions.branch.GitBranchActionsUtil
import git4idea.actions.branch.GitBranchActionsUtil.userWantsSyncControl
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions.EXPERIMENTAL_BRANCH_POPUP_ACTION_GROUP
import git4idea.ui.branch.tree.*
import javax.swing.tree.TreePath

class GitBranchesTreePopupStep(internal val project: Project,
                               internal val selectedRepository: GitRepository?,
                               internal val repositories: List<GitRepository>,
                               private val isFirstStep: Boolean) : PopupStep<Any> {

  internal val affectedRepositories get() = selectedRepository?.let(::listOf) ?: repositories

  private var finalRunnable: Runnable? = null

  override fun getFinalRunnable() = finalRunnable

  internal var treeModel: GitBranchesTreeModel
    private set

  private val topLevelItems = mutableListOf<Any>()

  init {
    if (ExperimentalUI.isNewUI() && isFirstStep) {
      val experimentalUIActionsGroup = ActionManager.getInstance().getAction(EXPERIMENTAL_BRANCH_POPUP_ACTION_GROUP) as? ActionGroup
      if (experimentalUIActionsGroup != null) {
        topLevelItems.addAll(
          createTopLevelActionItems(experimentalUIActionsGroup, project, selectedRepository, affectedRepositories).addSeparators())
        if (topLevelItems.isNotEmpty()) {
          topLevelItems.add(GitBranchesTreePopup.createTreeSeparator())
        }
      }
    }
    val actionGroup = ActionManager.getInstance().getAction(TOP_LEVEL_ACTION_GROUP) as? ActionGroup
    if (actionGroup != null) {
      // get selected repo inside actions
      topLevelItems.addAll(createTopLevelActionItems(actionGroup, project, selectedRepository, affectedRepositories).addSeparators())
      if (topLevelItems.isNotEmpty()) {
        topLevelItems.add(GitBranchesTreePopup.createTreeSeparator())
      }
    }

    treeModel = createTreeModel(false)
  }
  private fun createTreeModel(filterActive: Boolean): GitBranchesTreeModel {
    return when {
      !filterActive && repositories.size > 1
      && !userWantsSyncControl(project) && selectedRepository != null -> {
        GitBranchesTreeSelectedRepoModel(project, selectedRepository, repositories, topLevelItems)
      }
      filterActive && repositories.size > 1 -> GitBranchesTreeMultiRepoFilteringModel(project, repositories, topLevelItems)
      !filterActive && repositories.size > 1 -> GitBranchesTreeMultiRepoModel(project, repositories, topLevelItems)
      else -> GitBranchesTreeSingleRepoModel(project, repositories.first(), topLevelItems)
    }
  }

  private fun List<PopupFactoryImpl.ActionItem>.addSeparators(): List<Any> {
    val actionsWithSeparators = mutableListOf<Any>()
    for (action in this) {
      if (action.isPrependWithSeparator) {
        actionsWithSeparators.add(GitBranchesTreePopup.createTreeSeparator(action.separatorText))
      }
      actionsWithSeparators.add(action)
    }
    return actionsWithSeparators
  }

  fun isBranchesDiverged(): Boolean {
    return repositories.size > 1
           && repositories.diverged()
           && userWantsSyncControl(project)
  }

  fun getPreferredSelection(): TreePath? {
    return treeModel.getPreferredSelection()
  }

  fun createTreePathFor(value: Any): TreePath? {
    return createTreePathFor(treeModel, value)
  }

  internal fun setPrefixGrouping(state: Boolean) {
    treeModel.isPrefixGrouping = state
  }

  fun setSearchPattern(pattern: String?) {
    if (pattern == null || pattern == "/") {
      treeModel.filterBranches()
      return
    }

    val trimmedPattern = pattern.trim() //otherwise Character.isSpaceChar would affect filtering
    val matcher = PreferStartMatchMatcherWrapper(NameUtil.buildMatcher("*$trimmedPattern").build())
    treeModel.filterBranches(matcher)
  }

  fun updateTreeModelIfNeeded(tree: Tree, pattern: String?) {
    if (!isFirstStep || affectedRepositories.size == 1) {
      require(tree.model != null) { "Provided tree with null model" }
      return
    }

    val filterActive = !(pattern.isNullOrBlank() || pattern == "/")
    treeModel = createTreeModel(filterActive)
    tree.model = treeModel
  }

  override fun hasSubstep(selectedValue: Any?): Boolean {
    val userValue = selectedValue ?: return false
    return (userValue is GitRepository && treeModel !is GitBranchesTreeMultiRepoFilteringModel) ||
           userValue is GitBranch ||
           userValue is GitBranchesTreeModel.BranchUnderRepository ||
           (userValue is PopupFactoryImpl.ActionItem && userValue.isEnabled && userValue.action is ActionGroup)
  }

  fun isSelectable(node: Any?): Boolean {
    val userValue = node ?: return false
    return (userValue is GitRepository && treeModel !is GitBranchesTreeMultiRepoFilteringModel) ||
           userValue is GitBranch ||
           userValue is GitBranchesTreeModel.BranchUnderRepository ||
           (userValue is PopupFactoryImpl.ActionItem && userValue.isEnabled)
  }

  override fun onChosen(selectedValue: Any?, finalChoice: Boolean): PopupStep<out Any>? {
    if (selectedValue is GitRepository) {
      return GitBranchesTreePopupStep(project, selectedValue, listOf(selectedValue), false)
    }

    val branchUnderRepository = selectedValue as? GitBranchesTreeModel.BranchUnderRepository
    val branch = selectedValue as? GitBranch ?: branchUnderRepository?.branch

    if (branch != null) {
      val actionGroup = ActionManager.getInstance().getAction(BRANCH_ACTION_GROUP) as? ActionGroup ?: DefaultActionGroup()
      return createActionStep(actionGroup, project, selectedRepository,
                              branchUnderRepository?.repository?.let(::listOf) ?: affectedRepositories, branch)
    }

    if (selectedValue is PopupFactoryImpl.ActionItem) {
      if (!selectedValue.isEnabled) return FINAL_CHOICE
      val action = selectedValue.action
      if (action is ActionGroup && (!finalChoice || !selectedValue.isPerformGroup)) {
        return createActionStep(action, project, selectedRepository, affectedRepositories)
      }
      else {
        finalRunnable = Runnable {
          val place = if (isFirstStep) TOP_LEVEL_ACTION_PLACE else SINGLE_REPOSITORY_ACTION_PLACE
          ActionUtil.invokeAction(action, createDataContext(project, selectedRepository, affectedRepositories), place, null, null)
        }
      }
    }

    return FINAL_CHOICE
  }

  override fun getTitle(): String? =
    when {
      ExperimentalUI.isNewUI() -> null
      !isFirstStep -> null
      repositories.size > 1 -> DvcsBundle.message("branch.popup.vcs.name.branches", GitVcs.DISPLAY_NAME.get())
      else -> repositories.single().let {
        DvcsBundle.message("branch.popup.vcs.name.branches.in.repo", it.vcs.displayName, DvcsUtil.getShortRepositoryName(it))
      }
    }

  override fun canceled() {}

  override fun isMnemonicsNavigationEnabled() = false

  override fun getMnemonicNavigationFilter() = null

  override fun isSpeedSearchEnabled() = true

  override fun getSpeedSearchFilter() = SpeedSearchFilter<Any> { node ->
    when (node) {
      is GitBranch -> node.name
      else -> node?.let { GitBranchesTreeRenderer.getText(node, treeModel, repositories) } ?: ""
    }
  }

  override fun isAutoSelectionEnabled() = false

  companion object {
    internal const val HEADER_SETTINGS_ACTION_GROUP = "Git.Branches.Popup.Settings"
    private const val TOP_LEVEL_ACTION_GROUP = "Git.Branches.List"
    internal const val SPEED_SEARCH_DEFAULT_ACTIONS_GROUP = "Git.Branches.Popup.SpeedSearch"
    private const val BRANCH_ACTION_GROUP = "Git.Branch"

    internal val SINGLE_REPOSITORY_ACTION_PLACE = ActionPlaces.getPopupPlace("GitBranchesPopup.SingleRepo.Branch.Actions")
    internal val TOP_LEVEL_ACTION_PLACE = ActionPlaces.getPopupPlace("GitBranchesPopup.TopLevel.Branch.Actions")

    private fun createTopLevelActionItems(actionGroup: ActionGroup,
                                          project: Project,
                                          selectedRepository: GitRepository?,
                                          repositories: List<GitRepository>): List<PopupFactoryImpl.ActionItem> {
      val dataContext = createDataContext(project, selectedRepository, repositories)
      val actionItems = ActionPopupStep
        .createActionItems(actionGroup, dataContext, false, false, true, false, TOP_LEVEL_ACTION_PLACE, null)

      if (actionItems.singleOrNull()?.action == Utils.EMPTY_MENU_FILLER) {
        return emptyList()
      }

      return actionItems
    }

    private fun createActionStep(actionGroup: ActionGroup,
                                 project: Project,
                                 selectedRepository: GitRepository?,
                                 repositories: List<GitRepository>,
                                 branch: GitBranch? = null): ListPopupStep<*> {
      val dataContext = createDataContext(project, selectedRepository, repositories, branch)
      return JBPopupFactory.getInstance()
        .createActionsStep(actionGroup, dataContext, SINGLE_REPOSITORY_ACTION_PLACE, false, true, null, null, false, 0, false)
    }

    internal fun createDataContext(project: Project, selectedRepository: GitRepository?, repositories: List<GitRepository>, branch: GitBranch? = null): DataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(GitBranchActionsUtil.REPOSITORIES_KEY, repositories)
        .add(GitBranchActionsUtil.SELECTED_REPO_KEY, selectedRepository)
        .add(GitBranchActionsUtil.BRANCHES_KEY, branch?.let(::listOf))
        .build()

    /**
     * Adds weight to match offset. Degree of match is increased with the earlier the pattern was found in the name.
     */
    private class PreferStartMatchMatcherWrapper(private val delegate: MinusculeMatcher) : MinusculeMatcher() {
      override fun getPattern(): String = delegate.pattern

      override fun matchingFragments(name: String): FList<TextRange>? = delegate.matchingFragments(name)

      override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: FList<out TextRange>?): Int {
        var degree = delegate.matchingDegree(name, valueStartCaseMatch, fragments)
        if (fragments.isNullOrEmpty()) return degree
        degree += MATCH_OFFSET - fragments.head.startOffset
        return degree
      }

      companion object {
        private const val MATCH_OFFSET = 10000
      }
    }
  }
}
