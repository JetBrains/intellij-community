// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.branch.GroupingKey
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.FilteringSpeedSearch
import com.intellij.ui.tree.TreeTestUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ThreeState
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitTag
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.ui.branch.dashboard.*
import junit.framework.TestCase.assertEquals

abstract class GitBranchesTreeTest: LightPlatformTestCase() {
  internal fun branchesTreeTest(groupByDirectories: Boolean = true, groupByRepos: Boolean = false, test: GitBranchesTreeTestContext.() -> Unit) =
    with(GitBranchesTreeTestContext(groupByDirectories, groupByRepos, project)) { test() }
}

internal class GitBranchesTreeTestContext(private val groupByDirectories: Boolean, private val groupByRepos: Boolean, private val project: Project) {
  val tree = Tree()
  private val model = object : BranchesTreeModelBase() {
    override val groupingConfig: Map<GroupingKey, Boolean> = buildMap {
      this[GroupingKey.GROUPING_BY_REPOSITORY] = groupByRepos
      this[GroupingKey.GROUPING_BY_DIRECTORY] = groupByDirectories
    }

    fun setRefs(refs: RefsCollection, onlyMy: Boolean) {
      setTree(NodeDescriptorsModel.buildTreeNodes(
        project,
        refs,
        if (onlyMy) { ref -> (ref as? BranchInfo)?.isMy == ThreeState.YES } else { _ -> true },
        groupingConfig,
      ))
    }
  }
  val branchesTree = GitBranchesTestTree()
  private val searchTextField = branchesTree.installSearchField()

  fun assertTree(expected: String) {
    assertEquals("Tree state doesn't match expected. Search field - '${searchTextField.text}'", expected.trim(), printTree())
  }

  fun printTree(): String = TreeTestUtil(tree).setSelection(true).toString().trim()

  fun setState(
    localBranches: Collection<String>,
    remoteBranches: Collection<String>,
    tags: Collection<String> = emptyList(),
    expanded: Boolean = false,
  ) {
    val local = localBranches.map {
      BranchInfo(GitLocalBranch(it), isCurrent = false, isFavorite = false, repositories = emptyList())
    }
    val remote = remoteBranches.map {
      BranchInfo(GitStandardRemoteBranch(ORIGIN, it), isCurrent = false, isFavorite = false, repositories = emptyList())
    }
    val tags = tags.map {
      TagInfo(GitTag(it), isCurrent = false, isFavorite = false, repositories = emptyList())
    }
    setRawState(local, remote, tags, expanded)
  }

  fun setRawState(
    localBranches: Collection<BranchInfo>,
    remoteBranches: Collection<BranchInfo>,
    tags: Collection<TagInfo> = emptyList(),
    expanded: Boolean = false,
  ) {
    model.setRefs(RefsCollection(localBranches.toMutableSet(), remoteBranches.toMutableSet(), tags.toMutableSet()),
                  onlyMy = false)
    if (expanded) {
      TreeTestUtil(tree).expandAll()
    }
  }

  fun filter(filterText: String) {
    searchTextField.text = filterText
    branchesTree.speedSearch.refilter(searchTextField.text)
  }

  fun appendFilter(character: Char) {
    searchTextField.text += character
    branchesTree.speedSearch.refilter(searchTextField.text)
  }

  fun selectBranch(branch: String) {
    val speedSearch = branchesTree.speedSearch
    speedSearch.iterate(null, true).forEach { node ->
      if (branchesTree.getText(node.getNodeDescriptor()) == branch) {
        speedSearch.select(node)
        return
      }
    }
    throw AssertionError("Node with text $branch not found")
  }

  internal inner class GitBranchesTestTree : FilteringBranchesTreeBase(model, tree) {
    @Suppress("UNCHECKED_CAST")
    val speedSearch: FilteringSpeedSearch<BranchTreeNode, BranchNodeDescriptor>
      get() = searchModel.speedSearch as FilteringSpeedSearch<BranchTreeNode, BranchNodeDescriptor>

    init {
      model.addListener(object : BranchesTreeModel.Listener {
        override fun onTreeChange() {
          searchModel.updateStructure()
        }
      })
      searchModel.updateStructure()
    }
  }

  companion object {
    val ORIGIN_URLS = listOf("ssh://origin")
    val ORIGIN = GitRemote(GitRemote.ORIGIN, ORIGIN_URLS, ORIGIN_URLS, listOf(), listOf())
    val NOT_ORIGIN = GitRemote("not-origin", ORIGIN_URLS, ORIGIN_URLS, listOf(), listOf())

    fun branchInfo(branch: GitBranch, isCurrent: Boolean = false, isFavorite: Boolean = false, repositories: List<GitRepository> = emptyList()) =
      BranchInfo(branch, isCurrent, isFavorite, repositories = repositories)

    fun tagInfo(tag: GitTag, isCurrent: Boolean = false, isFavorite: Boolean = false, repositories: List<GitRepository> = emptyList()) =
      TagInfo(tag, isCurrent, isFavorite, repositories)
  }
}

