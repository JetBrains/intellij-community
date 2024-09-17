// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.branch.GroupingKey
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.FilteringSpeedSearch
import com.intellij.ui.tree.TreeTestUtil
import com.intellij.ui.treeStructure.Tree
import git4idea.GitLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.repo.GitRemote
import git4idea.ui.branch.dashboard.*
import junit.framework.TestCase.assertEquals

class GitBranchesTreeTest: LightPlatformTestCase() {
  fun `test another branch is not selected if current matches search field`() = branchesTreeTest {
    setState(localBranches = listOf("main-123", "main"), remoteBranches = listOf("main"))
    selectBranch("main-123")

    // Remote node collapsed
    val expectedBeforeTyping = """
      |-ROOT
      | HEAD_NODE
      | -LOCAL_ROOT
      |  BRANCH:main
      |  [BRANCH:main-123]
      | +REMOTE_ROOT
    """.trimMargin()
    assertTree(expectedBeforeTyping)

    // Remote node expanded
    val expectedDuringTyping = """
      |-ROOT
      | HEAD_NODE
      | -LOCAL_ROOT
      |  BRANCH:main
      |  [BRANCH:main-123]
      | -REMOTE_ROOT
      |  -GROUP_NODE:origin
      |   BRANCH:origin/main
    """.trimMargin()

    "mai".toCharArray().forEach { char ->
      searchTextField.text += char
      assertTree(expectedDuringTyping)
    }
  }

  fun `test selection is changed to match search field`() = branchesTreeTest {
    setState(localBranches = listOf("1", "2", "3", "main"), remoteBranches = listOf("main"))
    selectBranch("1")

    searchTextField.text = "main"
    assertTree("""
      |-ROOT
      | HEAD_NODE
      | -LOCAL_ROOT
      |  [BRANCH:main]
      | -REMOTE_ROOT
      |  -GROUP_NODE:origin
      |   BRANCH:origin/main
    """.trimMargin())
  }

  fun `test selection is updated on exact match`() = branchesTreeTest {
    setState(localBranches = listOf("main-123", "main"), remoteBranches = listOf("main"))
    selectBranch("main-123")

    searchTextField.text = "mai"
    val expectedSelectionNotUpdated = """
      |-ROOT
      | HEAD_NODE
      | -LOCAL_ROOT
      |  BRANCH:main
      |  [BRANCH:main-123]
      | -REMOTE_ROOT
      |  -GROUP_NODE:origin
      |   BRANCH:origin/main
    """.trimMargin()
    assertTree(expectedSelectionNotUpdated)

    searchTextField.text = "main"
    val expectedSelectionUpdated = """
      |-ROOT
      | HEAD_NODE
      | -LOCAL_ROOT
      |  [BRANCH:main]
      |  BRANCH:main-123
      | -REMOTE_ROOT
      |  -GROUP_NODE:origin
      |   BRANCH:origin/main
    """.trimMargin()
    assertTree(expectedSelectionUpdated)
  }

  fun `test exact match of branch name and group node`() = branchesTreeTest {
    setState(localBranches = listOf("main/123", "main"), remoteBranches = listOf())
    selectBranch("main/123")
    searchTextField.text = "main"
    assertTree("""
      |-ROOT
      | HEAD_NODE
      | -LOCAL_ROOT
      |  -GROUP_NODE:main
      |   BRANCH:main/123
      |  [BRANCH:main]
    """.trimMargin())
  }

  fun `test selection of remote`() = branchesTreeTest {
    setState(localBranches = listOf("main"), remoteBranches = listOf("main", "ish/242", "a/242/b", "242", "242/fix"))

    searchTextField.text = "242"
    assertTree("""
      |-ROOT
      | HEAD_NODE
      | LOCAL_ROOT
      | -REMOTE_ROOT
      |  -GROUP_NODE:origin
      |   -GROUP_NODE:242
      |    BRANCH:origin/242/fix
      |   -GROUP_NODE:a
      |    -GROUP_NODE:242
      |     BRANCH:origin/a/242/b
      |   -GROUP_NODE:ish
      |    BRANCH:origin/ish/242
      |   [BRANCH:origin/242]
    """.trimMargin())
  }

  fun `test selection of remote with no grouping`() = branchesTreeTest(groupByDirectories = false) {
    setState(localBranches = listOf("main"), remoteBranches = listOf("main", "ish/242", "a/242/b", "242", "242/fix"))

    searchTextField.text = "242"
    assertTree("""
     |-ROOT
     | HEAD_NODE
     | LOCAL_ROOT
     | -REMOTE_ROOT
     |  [BRANCH:origin/242]
     |  BRANCH:origin/242/fix
     |  BRANCH:origin/a/242/b
     |  BRANCH:origin/ish/242
    """.trimMargin())
  }
}

private fun branchesTreeTest(groupByDirectories: Boolean = true, test: TestContext.() -> Unit) = with(TestContext(groupByDirectories)) { test() }

private class TestContext(groupByDirectories: Boolean) {
  val tree = Tree()
  val branchesTree = GitBranchesTestTree(tree, groupByDirectories = groupByDirectories)
  val searchTextField = branchesTree.installSearchField()

  fun assertTree(expected: String) {
    assertEquals("Search field - ${searchTextField.text}", expected.trim(), TreeTestUtil(tree).setSelection(true).toString().trim())
  }

  fun setState(localBranches: Collection<String>, remoteBranches: Collection<String>) {
    val local = localBranches.map {
      BranchInfo(GitLocalBranch(it), isLocal = true, isCurrent = false, isFavorite = false, repositories = emptyList())
    }
    val remote = remoteBranches.map {
      BranchInfo(GitStandardRemoteBranch(ORIGIN, it), isLocal = false, isCurrent = false, isFavorite = false, repositories = emptyList())
    }
    branchesTree.refreshNodeDescriptorsModel(localBranches = local, remoteBranches = remote, showOnlyMy = false)
    branchesTree.searchModel.updateStructure()
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

  companion object {
    private val ORIGIN_URLS = listOf("ssh://origin")
    private val ORIGIN = GitRemote(GitRemote.ORIGIN, ORIGIN_URLS, ORIGIN_URLS, listOf(), listOf())
  }
}

internal class GitBranchesTestTree(
  tree: Tree,
  groupByDirectories: Boolean,
): FilteringBranchesTreeBase(tree, BranchTreeNode(BranchNodeDescriptor(NodeType.ROOT))) {
  @Suppress("UNCHECKED_CAST")
  val speedSearch: FilteringSpeedSearch<BranchTreeNode, BranchNodeDescriptor>
    get() = searchModel.speedSearch as FilteringSpeedSearch<BranchTreeNode, BranchNodeDescriptor>

  override val groupingConfig: Map<GroupingKey, Boolean> = buildMap {
    this[GroupingKey.GROUPING_BY_REPOSITORY] = false
    this[GroupingKey.GROUPING_BY_DIRECTORY] = groupByDirectories
  }
}