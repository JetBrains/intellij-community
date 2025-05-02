// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.SpeedSearchFilter
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.treeStructure.Tree
import com.intellij.vcs.git.shared.repo.GitRepositoriesFrontendHolder
import git4idea.GitBranch
import git4idea.GitReference
import git4idea.branch.GitRefType
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchesMatcherWrapper
import git4idea.ui.branch.getCommonText
import git4idea.ui.branch.getInRepoText
import git4idea.ui.branch.getText
import git4idea.ui.branch.tree.GitBranchesTreeModel
import git4idea.ui.branch.tree.GitBranchesTreeModel.RefUnderRepository
import git4idea.ui.branch.tree.createTreePathFor
import javax.swing.tree.TreePath

internal abstract class GitBranchesTreePopupStepBase(
  internal val project: Project,
  internal val selectedRepository: GitRepository?,
  internal val repositories: List<GitRepository>,
) : PopupStep<Any> {
  internal val affectedRepositories = selectedRepository?.let(::listOf) ?: repositories
  internal val affectedRepositoriesIds = affectedRepositories.map { it.rpcId }
  internal val affectedRepositoriesFrontendModel = GitRepositoriesFrontendHolder.getInstance(project).let { holder ->
    affectedRepositoriesIds.map { holder.get(it) }
  }

  internal abstract val treeModel: GitBranchesTreeModel

  protected abstract fun createTreeModel(filterActive: Boolean): GitBranchesTreeModel
  protected abstract fun setTreeModel(treeModel: GitBranchesTreeModel)

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
      treeModel.applyFilterAndRebuild(null)
      return
    }

    val trimmedPattern = pattern.trim() //otherwise Character.isSpaceChar would affect filtering
    val matcher = GitBranchesMatcherWrapper(NameUtil.buildMatcher("*$trimmedPattern").build())
    treeModel.applyFilterAndRebuild(matcher)
  }

  fun updateTreeModelIfNeeded(tree: Tree, pattern: String?) {
    if (shouldValidateNotNullTreeModel()) {
      require(tree.model != null) { "Provided tree with null model" }
      return
    }

    val filterActive = !(pattern.isNullOrBlank() || pattern == "/")
    setTreeModel(createTreeModel(filterActive))
    tree.model = treeModel
  }

  protected open fun shouldValidateNotNullTreeModel() = affectedRepositories.size == 1

  override fun hasSubstep(selectedValue: Any?): Boolean {
    val userValue = selectedValue ?: return false

    return if (userValue is PopupFactoryImpl.ActionItem) {
      userValue.isEnabled && userValue.action is ActionGroup
    }
    else {
      treeModel.isSelectable(selectedValue)
    }
  }

  fun isSelectable(node: Any?): Boolean {
    return treeModel.isSelectable(node)
  }

  override fun canceled() {}

  override fun isMnemonicsNavigationEnabled() = false

  override fun getMnemonicNavigationFilter() = null

  override fun isSpeedSearchEnabled() = true

  override fun getSpeedSearchFilter() = SpeedSearchFilter<Any> { node ->
    when (node) {
      is GitBranch -> node.name
      else -> node?.let { getNodeText(node) } ?: ""
    }
  }

  internal fun getNodeText(node: Any?): @NlsSafe String? {
    val value = node ?: return null
    return when (value) {
      is GitRefType -> when {
        selectedRepository != null -> value.getInRepoText(DvcsUtil.getShortRepositoryName(selectedRepository))
        repositories.size > 1 -> value.getCommonText()
        else -> value.getText()
      }
      is GitBranchesTreeModel.BranchesPrefixGroup -> value.prefix.last()
      is GitBranchesTreeModel.RefTypeUnderRepository -> value.type.getText()
      is RefUnderRepository -> getRefText(value.ref, treeModel.isPrefixGrouping)
      is GitReference -> getRefText(value, treeModel.isPrefixGrouping)
      is PopupFactoryImpl.ActionItem -> value.text
      is GitBranchesTreeModel.PresentableNode -> value.presentableText
      else -> null
    }
  }

  private fun getRefText(value: GitReference, prefixGrouping: Boolean): String =
    if (prefixGrouping) value.name.split('/').last() else value.name

  override fun isAutoSelectionEnabled() = false
}
