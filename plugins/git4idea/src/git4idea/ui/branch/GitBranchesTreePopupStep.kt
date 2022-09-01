// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.ui.DvcsBundle
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
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.PlatformIcons
import com.intellij.util.containers.FList
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.actions.branch.GitBranchActionsUtil
import git4idea.branch.GitBranchType
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import icons.DvcsImplIcons
import javax.swing.Icon
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

class GitBranchesTreePopupStep(private val project: Project, internal val repository: GitRepository) : PopupStep<Any> {

  private var finalRunnable: Runnable? = null

  override fun getFinalRunnable() = finalRunnable

  private val _treeModel: GitBranchesTreeModel
  val treeModel: TreeModel
    get() = _treeModel


  init {
    val topLevelActions = mutableListOf<PopupFactoryImpl.ActionItem>()
    val actionGroup = ActionManager.getInstance().getAction(TOP_LEVEL_ACTION_GROUP) as? ActionGroup
    if (actionGroup != null) {
      topLevelActions.addAll(createActionItems(actionGroup, project, repository))
    }

    _treeModel = GitBranchesTreeModelImpl(project, repository, topLevelActions)
  }

  fun getPreferredSelection(): TreePath? {
    return _treeModel.getPreferredSelection()
  }

  internal fun isSeparatorAboveRequired(path: TreePath) = path.lastPathComponent == GitBranchType.LOCAL

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

  override fun hasSubstep(selectedValue: Any?): Boolean {
    val userValue = selectedValue ?: return false
    return userValue is GitBranch ||
           (userValue is PopupFactoryImpl.ActionItem && userValue.isEnabled && userValue.action is ActionGroup)
  }

  fun isSelectable(node: Any?): Boolean {
    val userValue = node ?: return false
    return userValue is GitBranch ||
           (userValue is PopupFactoryImpl.ActionItem && userValue.isEnabled)
  }

  override fun onChosen(selectedValue: Any?, finalChoice: Boolean): PopupStep<out Any>? {
    if (selectedValue is GitBranch) {
      val actionGroup = ActionManager.getInstance().getAction(BRANCH_ACTION_GROUP) as? ActionGroup ?: DefaultActionGroup()
      return createActionStep(actionGroup, project, repository, selectedValue)
    }

    if (selectedValue is PopupFactoryImpl.ActionItem) {
      if (!selectedValue.isEnabled) return FINAL_CHOICE
      val action = selectedValue.action
      if (action is ActionGroup && (!finalChoice || !selectedValue.isPerformGroup)) {
        return createActionStep(action, project, repository)
      }
      else {
        finalRunnable = Runnable {
          ActionUtil.invokeAction(action, createDataContext(project, repository), ACTION_PLACE, null, null)
        }
      }
    }

    return FINAL_CHOICE
  }

  override fun getTitle(): String =
    DvcsBundle.message("branch.popup.vcs.name.branches.in.repo", repository.vcs.displayName, DvcsUtil.getShortRepositoryName(repository))

  fun getIcon(treeNode: Any?): Icon? {
    val value = treeNode ?: return null
    return when (value) {
      is GitBranchesTreeModel.BranchesPrefixGroup -> PlatformIcons.FOLDER_ICON
      is GitBranch -> getBranchIcon(value)
      else -> null
    }
  }

  private fun getBranchIcon(branch: GitBranch): Icon {
    val isCurrent = repository.currentBranch == branch
    val isFavorite = project.service<GitBranchManager>().isFavorite(GitBranchType.of(branch), repository, branch.name)

    return when {
      isCurrent && isFavorite -> DvcsImplIcons.CurrentBranchFavoriteLabel
      isCurrent -> DvcsImplIcons.CurrentBranchLabel
      isFavorite -> AllIcons.Nodes.Favorite
      else -> AllIcons.Vcs.BranchNode
    }
  }

  fun getText(treeNode: Any?): @NlsSafe String? {
    val value = treeNode ?: return null
    return when (value) {
      GitBranchType.LOCAL -> GitBundle.message("group.Git.Local.Branch.title")
      GitBranchType.REMOTE -> GitBundle.message("group.Git.Remote.Branch.title")
      is GitBranchesTreeModel.BranchesPrefixGroup -> value.prefix.last()
      is GitBranch -> value.name.split('/').last()
      is PopupFactoryImpl.ActionItem -> value.text
      else -> value.toString()
    }
  }

  fun getSecondaryText(treeNode: Any?): @NlsSafe String? {
    return when (treeNode) {
      is PopupFactoryImpl.ActionItem -> KeymapUtil.getFirstKeyboardShortcutText(treeNode.action)
      is GitLocalBranch -> treeNode.findTrackedBranch(repository)?.name
      else -> null
    }
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
    private const val BRANCH_ACTION_GROUP = "Git.Branch"

    internal val ACTION_PLACE = ActionPlaces.getPopupPlace("GitBranchesPopup")

    private fun createActionItems(actionGroup: ActionGroup,
                                  project: Project,
                                  repository: GitRepository): List<PopupFactoryImpl.ActionItem> {
      val dataContext = createDataContext(project, repository)
      return ActionPopupStep
        .createActionItems(actionGroup, dataContext, false, false, false, false, ACTION_PLACE, null)
    }

    private fun createActionStep(actionGroup: ActionGroup,
                                 project: Project,
                                 repository: GitRepository,
                                 branch: GitBranch? = null): ListPopupStep<*> {
      val dataContext = createDataContext(project, repository, branch)
      return JBPopupFactory.getInstance()
        .createActionsStep(actionGroup, dataContext, ACTION_PLACE, false, true, null, null, true, 0, false)
    }

    internal fun createDataContext(project: Project, repository: GitRepository, branch: GitBranch? = null): DataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(GitBranchActionsUtil.REPOSITORIES_KEY, listOf(repository))
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
