// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.diverged
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.PopupStep.FINAL_CHOICE
import com.intellij.openapi.ui.popup.SpeedSearchFilter
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.RowIcon
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.PlatformIcons
import com.intellij.util.containers.FList
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.GitVcs
import git4idea.actions.branch.GitBranchActionsUtil
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchType
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions.EXPERIMENTAL_BRANCH_POPUP_ACTION_GROUP
import icons.DvcsImplIcons
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

class GitBranchesTreePopupStep(private val project: Project,
                               internal val repositories: List<GitRepository>,
                               private val isFirstStep: Boolean) : PopupStep<Any> {

  private var finalRunnable: Runnable? = null

  override fun getFinalRunnable() = finalRunnable

  private var _treeModel: GitBranchesTreeModel
  val treeModel: TreeModel
    get() = _treeModel

  private val topLevelItems = mutableListOf<Any>()

  init {
    if (ExperimentalUI.isNewUI() && isFirstStep) {
      val experimentalUIActionsGroup = ActionManager.getInstance().getAction(EXPERIMENTAL_BRANCH_POPUP_ACTION_GROUP) as? ActionGroup
      if (experimentalUIActionsGroup != null) {
        topLevelItems.addAll(createActionItems(experimentalUIActionsGroup, project, repositories).addSeparators())
        topLevelItems.add(GitBranchesTreePopup.createTreeSeparator())
      }
    }
    val actionGroup = ActionManager.getInstance().getAction(TOP_LEVEL_ACTION_GROUP) as? ActionGroup
    if (actionGroup != null) {
      // get selected repo inside actions
      topLevelItems.addAll(createActionItems(actionGroup, project, repositories).addSeparators())
      topLevelItems.add(GitBranchesTreePopup.createTreeSeparator())
    }

    _treeModel = createTreeModel(false)
  }
  private fun createTreeModel(filterActive: Boolean): GitBranchesTreeModel {
    return when {
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
           && GitBranchActionsUtil.userWantsSyncControl(project)
  }

  fun getPreferredSelection(): TreePath? {
    return _treeModel.getPreferredSelection()
  }

  fun createTreePathFor(value: Any): TreePath? {
    return createTreePathFor(_treeModel, value)
  }

  internal fun setPrefixGrouping(state: Boolean) {
    _treeModel.isPrefixGrouping = state
  }

  private val LOCAL_SEARCH_PREFIX = "/l"
  private val REMOTE_SEARCH_PREFIX = "/r"

  fun setSearchPattern(pattern: String?) {
    if (pattern == null || pattern == "/") {
      _treeModel.filterBranches()
      return
    }

    var branchType: GitBranchType? = null
    var processedPattern = pattern

    if (pattern.startsWith(LOCAL_SEARCH_PREFIX)) {
      branchType = GitBranchType.LOCAL
      processedPattern = pattern.removePrefix(LOCAL_SEARCH_PREFIX).trimStart()
    }

    if (pattern.startsWith(REMOTE_SEARCH_PREFIX)) {
      branchType = GitBranchType.REMOTE
      processedPattern = pattern.removePrefix(REMOTE_SEARCH_PREFIX).trimStart()
    }

    val matcher = PreferStartMatchMatcherWrapper(NameUtil.buildMatcher("*$processedPattern").build())
    _treeModel.filterBranches(branchType, matcher)
  }

  fun updateTreeModelIfNeeded(tree: Tree, pattern: String?) {
    if (!isFirstStep || repositories.size == 1) return

    val filterActive = !(pattern.isNullOrBlank() || pattern == "/")
    _treeModel = createTreeModel(filterActive)
    tree.model = _treeModel
  }

  override fun hasSubstep(selectedValue: Any?): Boolean {
    val userValue = selectedValue ?: return false
    return (userValue is GitRepository && _treeModel !is GitBranchesTreeMultiRepoFilteringModel) ||
           userValue is GitBranch ||
           userValue is GitBranchesTreeModel.BranchUnderRepository ||
           (userValue is PopupFactoryImpl.ActionItem && userValue.isEnabled && userValue.action is ActionGroup)
  }

  fun isSelectable(node: Any?): Boolean {
    val userValue = node ?: return false
    return (userValue is GitRepository && _treeModel !is GitBranchesTreeMultiRepoFilteringModel) ||
           userValue is GitBranch ||
           userValue is GitBranchesTreeModel.BranchUnderRepository ||
           (userValue is PopupFactoryImpl.ActionItem && userValue.isEnabled)
  }

  override fun onChosen(selectedValue: Any?, finalChoice: Boolean): PopupStep<out Any>? {
    if (selectedValue is GitRepository) {
      return GitBranchesTreePopupStep(project, listOf(selectedValue), false)
    }

    val branchUnderRepository = selectedValue as? GitBranchesTreeModel.BranchUnderRepository
    val branch = selectedValue as? GitBranch ?: branchUnderRepository?.branch

    if (branch != null) {
      val actionGroup = ActionManager.getInstance().getAction(BRANCH_ACTION_GROUP) as? ActionGroup ?: DefaultActionGroup()
      return createActionStep(actionGroup, project, branchUnderRepository?.repository?.let(::listOf) ?: repositories, branch)
    }

    if (selectedValue is PopupFactoryImpl.ActionItem) {
      if (!selectedValue.isEnabled) return FINAL_CHOICE
      val action = selectedValue.action
      if (action is ActionGroup && (!finalChoice || !selectedValue.isPerformGroup)) {
        return createActionStep(action, project, repositories)
      }
      else {
        finalRunnable = Runnable {
          ActionUtil.invokeAction(action, createDataContext(project, repositories), ACTION_PLACE, null, null)
        }
      }
    }

    return FINAL_CHOICE
  }

  override fun getTitle(): String? =
    when {
      !isFirstStep -> null
      repositories.size > 1 -> DvcsBundle.message("branch.popup.vcs.name.branches", GitVcs.DISPLAY_NAME.get())
      else -> repositories.single().let {
        DvcsBundle.message("branch.popup.vcs.name.branches.in.repo", it.vcs.displayName, DvcsUtil.getShortRepositoryName(it))
      }
    }

  fun getIncomingOutgoingIconWithTooltip(treeNode: Any?): Pair<Icon?, @Nls(capitalization = Nls.Capitalization.Sentence) String?> {
    val empty = null to null
    val value = treeNode ?: return empty
    return when (value) {
      is GitBranch -> getIncomingOutgoingIconWithTooltip(value)
      else -> empty
    }
  }

  private fun getIncomingOutgoingIconWithTooltip(branch: GitBranch): Pair<Icon?, String?> {
    val branchName = branch.name
    val incomingOutgoingManager = project.service<GitBranchIncomingOutgoingManager>()
    val hasIncoming =
      repositories.any { incomingOutgoingManager.hasIncomingFor(it, branchName) }

    val hasOutgoing =
      repositories.any { incomingOutgoingManager.hasOutgoingFor(it, branchName) }

    val tooltip = GitBranchPopupActions.LocalBranchActions.constructIncomingOutgoingTooltip(hasIncoming, hasOutgoing).orEmpty()

    return when {
      hasIncoming && hasOutgoing -> RowIcon(DvcsImplIcons.Incoming, DvcsImplIcons.Outgoing)
      hasIncoming -> DvcsImplIcons.Incoming
      hasOutgoing -> DvcsImplIcons.Outgoing
      else -> null
    } to tooltip
  }

  private val colorManager = RepositoryChangesBrowserNode.getColorManager(project)

  fun getNodeIcon(treeNode: Any?, isSelected: Boolean): Icon? {
    val value = treeNode ?: return null
    return when (value) {
      is PopupFactoryImpl.ActionItem -> value.getIcon(isSelected)
      is GitRepository -> RepositoryChangesBrowserNode.getRepositoryIcon(value, colorManager)
      else -> null
    }
  }

  fun getIcon(treeNode: Any?, isSelected: Boolean): Icon? {
    val value = treeNode ?: return null
    return when (value) {
      is GitBranchesTreeModel.BranchesPrefixGroup -> PlatformIcons.FOLDER_ICON
      is GitBranchesTreeModel.BranchUnderRepository -> getBranchIcon(value.branch, listOf(value.repository), isSelected)
      is GitBranch -> getBranchIcon(value, repositories, isSelected)
      else -> null
    }
  }

  private fun getBranchIcon(branch: GitBranch, repositories: List<GitRepository>, isSelected: Boolean): Icon {
    val isCurrent = repositories.all { it.currentBranch == branch }
    val branchManager = project.service<GitBranchManager>()
    val isFavorite = repositories.all { branchManager.isFavorite(GitBranchType.of(branch), it, branch.name) }

    return when {
      isSelected && isFavorite -> AllIcons.Nodes.Favorite
      isSelected -> AllIcons.Nodes.NotFavoriteOnHover
      isCurrent && isFavorite -> DvcsImplIcons.CurrentBranchFavoriteLabel
      isCurrent -> DvcsImplIcons.CurrentBranchLabel
      isFavorite -> AllIcons.Nodes.Favorite
      else -> AllIcons.Vcs.BranchNode
    }
  }

  fun getText(treeNode: Any?): @NlsSafe String? {
    val value = treeNode ?: return null
    return when (value) {
      GitBranchType.LOCAL -> {
        if (repositories.size > 1) GitBundle.message("common.local.branches") else GitBundle.message("group.Git.Local.Branch.title")
      }
      GitBranchType.REMOTE -> {
        if (repositories.size > 1) GitBundle.message("common.remote.branches") else GitBundle.message("group.Git.Remote.Branch.title")
      }
      is GitBranchesTreeModel.BranchesPrefixGroup -> value.prefix.last()
      is GitRepository -> DvcsUtil.getShortRepositoryName(value)
      is GitBranchesTreeModel.BranchTypeUnderRepository -> {
        when (value.type) {
          GitBranchType.LOCAL -> GitBundle.message("group.Git.Local.Branch.title")
          GitBranchType.REMOTE -> GitBundle.message("group.Git.Remote.Branch.title")
        }
      }
      is GitBranchesTreeModel.BranchUnderRepository -> getText(value.branch)
      is GitBranch -> {
        if (_treeModel.isPrefixGrouping) value.name.split('/').last() else value.name
      }
      is PopupFactoryImpl.ActionItem -> value.text
      else -> value.toString()
    }
  }

  fun getSecondaryText(treeNode: Any?): @NlsSafe String? {
    return when (treeNode) {
      is PopupFactoryImpl.ActionItem -> KeymapUtil.getFirstKeyboardShortcutText(treeNode.action)
      is GitRepository -> treeNode.currentBranch?.name.orEmpty()
      is GitLocalBranch -> {
        treeNode.getCommonTrackedBranch(repositories)?.name
      }
      else -> null
    }
  }

  private fun GitLocalBranch.getCommonTrackedBranch(repositories: List<GitRepository>): GitRemoteBranch? {
    var commonTrackedBranch: GitRemoteBranch? = null

    for (repository in repositories) {
      val trackedBranch = findTrackedBranch(repository) ?: return null

      if (commonTrackedBranch == null) {
        commonTrackedBranch = trackedBranch
      }
      else if (commonTrackedBranch.name != trackedBranch.name) {
        return null
      }
    }
    return commonTrackedBranch
  }

  override fun canceled() {}

  override fun isMnemonicsNavigationEnabled() = false

  override fun getMnemonicNavigationFilter() = null

  override fun isSpeedSearchEnabled() = true

  override fun getSpeedSearchFilter() = SpeedSearchFilter<Any> { node ->
    when (node) {
      is GitBranch -> node.name
      else -> node?.let(::getText) ?: ""
    }
  }

  override fun isAutoSelectionEnabled() = false

  companion object {
    internal const val HEADER_SETTINGS_ACTION_GROUP = "Git.Branches.Popup.Settings"
    private const val TOP_LEVEL_ACTION_GROUP = "Git.Branches.List"
    internal const val SPEED_SEARCH_DEFAULT_ACTIONS_GROUP = "Git.Branches.Popup.SpeedSearch"
    private const val BRANCH_ACTION_GROUP = "Git.Branch"

    internal val ACTION_PLACE = ActionPlaces.getPopupPlace("GitBranchesPopup")

    private fun createActionItems(actionGroup: ActionGroup,
                                  project: Project,
                                  repositories: List<GitRepository>): List<PopupFactoryImpl.ActionItem> {
      val dataContext = createDataContext(project, repositories)
      return ActionPopupStep
        .createActionItems(actionGroup, dataContext, false, false, false, false, ACTION_PLACE, null)
    }

    private fun createActionStep(actionGroup: ActionGroup,
                                 project: Project,
                                 repositories: List<GitRepository>,
                                 branch: GitBranch? = null): ListPopupStep<*> {
      val dataContext = createDataContext(project, repositories, branch)
      return JBPopupFactory.getInstance()
        .createActionsStep(actionGroup, dataContext, ACTION_PLACE, false, true, null, null, true, 0, false)
    }

    internal fun createDataContext(project: Project, repositories: List<GitRepository>, branch: GitBranch? = null): DataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(GitBranchActionsUtil.REPOSITORIES_KEY, repositories)
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
