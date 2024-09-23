// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.repo.GitRepository
import javax.swing.tree.TreePath

internal class BranchesTreeSelection(val selectionPaths: Array<TreePath>?) {
  fun getSelectedBranches(): List<BranchInfo> =
    getSelectedNodeDescriptors()
      .filterIsInstance<BranchNodeDescriptor.Branch>()
      .map { it.branchInfo }
      .toList()

  fun getSelectedRefs(): List<RefInfo> =
    getSelectedNodeDescriptors()
      .filterIsInstance<BranchNodeDescriptor.Ref>()
      .map { it.refInfo }
      .toList()

  fun getSelectedBranchesWithRepositories(): List<Pair<BranchInfo, List<GitRepository>>> =
    getSelectedNodes().mapNotNull { node ->
      val nodeDescriptor = node.getNodeDescriptor()
      if (nodeDescriptor is BranchNodeDescriptor.Branch) {
        nodeDescriptor.branchInfo to getSelectedRepositories(node)
      }
      else null
    }.toList()

  fun getSelectedRemotes(): Set<RemoteInfo> =
    getSelectedNodes().mapNotNull {
      val descriptor = it.getNodeDescriptor()
      val parentDescriptor = it.parent?.getNodeDescriptor()
      if (descriptor is BranchNodeDescriptor.RemoteGroup) {
        RemoteInfo(descriptor.remote.name, (parentDescriptor as? BranchNodeDescriptor.Repository)?.repository)
      }
      else null
    }.toSet()

  fun getRepositoriesForSelectedBranches() = getSelectedNodes().flatMap { getSelectedRepositories(it) }.toSet()

  fun getSelectedRepositories(branchInfo: BranchInfo): List<GitRepository> {
    val branchNode = getSelectedNodes().find { node ->
      val nodeDescriptor = node.getNodeDescriptor()
      nodeDescriptor is BranchNodeDescriptor.Branch && nodeDescriptor.branchInfo == branchInfo
    } ?: return emptyList()

    return getSelectedRepositories(branchNode)
  }

  fun getSelectedBranchFilters(): List<String> =
    getSelectedNodeDescriptors().mapNotNull {
      when (it) {
        is BranchNodeDescriptor.Branch -> it.branchInfo.branchName
        BranchNodeDescriptor.Head -> VcsLogUtil.HEAD
        else -> null
      }
    }.toList()

  fun isHeadSelected(): Boolean = getSelectedNodeDescriptors().any { it == BranchNodeDescriptor.Head  }

  fun getSelectedNodeDescriptors(): Sequence<BranchNodeDescriptor> =
    getSelectedNodes().map { it.getNodeDescriptor() }

  fun getSelectedNodes(): Sequence<BranchTreeNode> {
    val paths = selectionPaths ?: return emptySequence()
    return paths.asSequence()
      .map(TreePath::getLastPathComponent)
      .filterIsInstance<BranchTreeNode>()
  }

  companion object {
    fun getSelectedRepositories(node: BranchTreeNode): List<GitRepository> {
      val repoNode = findNodeDescriptorInPath(node) { descriptor ->
        descriptor is BranchNodeDescriptor.Repository
      }

      val repoInPath = (repoNode as? BranchNodeDescriptor.Repository)?.repository
      return if (repoInPath != null) listOf(repoInPath) else (node.getNodeDescriptor() as? BranchNodeDescriptor.Ref)?.refInfo?.repositories.orEmpty()
    }

    private fun findNodeDescriptorInPath(node: BranchTreeNode, condition: (BranchNodeDescriptor) -> Boolean): BranchNodeDescriptor? {
      var curNode: BranchTreeNode? = node
      while (curNode != null) {
        val node = curNode.parent
        if (node != null && condition(node.getNodeDescriptor())) return node.getNodeDescriptor()
        curNode = node
      }
      return null
    }
  }
}