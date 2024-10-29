// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.repo.GitRepository
import javax.swing.tree.TreePath

internal class BranchesTreeSelection(selectionPaths: Array<TreePath>?) {
  val selectedNodes: List<BranchTreeNode> =
    selectionPaths?.mapNotNull { it.lastPathComponent as? BranchTreeNode } ?: emptyList()

  val selectedNodeDescriptors: List<BranchNodeDescriptor> =
    selectedNodes.map { it.getNodeDescriptor() }

  val selectedBranches: List<BranchInfo>
    get() = selectedNodeDescriptors.mapNotNull { (it as? BranchNodeDescriptor.Branch)?.branchInfo }

  val selectedRefs: List<RefInfo>
    get() = selectedNodeDescriptors.mapNotNull { (it as? BranchNodeDescriptor.Ref)?.refInfo }

  val headSelected: Boolean
    get() = selectedNodeDescriptors.any { it == BranchNodeDescriptor.Head }

  val selectedRemotes: Set<RemoteInfo>
    get() = selectedNodes.mapNotNullTo(mutableSetOf()) {
      val descriptor = it.getNodeDescriptor()
      val parentDescriptor = it.parent?.getNodeDescriptor()
      if (descriptor is BranchNodeDescriptor.RemoteGroup) {
        RemoteInfo(descriptor.remote.name, (parentDescriptor as? BranchNodeDescriptor.Repository)?.repository)
      }
      else null
    }

  val selectedBranchFilters: List<String>
    get() = selectedNodeDescriptors.mapNotNull {
      when (it) {
        is BranchNodeDescriptor.Branch -> it.branchInfo.branchName
        BranchNodeDescriptor.Head -> VcsLogUtil.HEAD
        else -> null
      }
    }

  val repositoriesOfSelectedBranches: Set<GitRepository>
    get() = selectedBranches.flatMapTo(mutableSetOf()) { getSelectedRepositories(it) }

  val selectedBranchesToRepositories: List<Pair<BranchInfo, List<GitRepository>>>
    get() = selectedNodes.mapNotNull { node ->
      val nodeDescriptor = node.getNodeDescriptor()
      if (nodeDescriptor is BranchNodeDescriptor.Branch) {
        nodeDescriptor.branchInfo to getSelectedRepositories(node)
      }
      else null
    }

  fun getSelectedRepositories(branchInfo: BranchInfo): List<GitRepository> {
    val branchNode = selectedNodes.find { node ->
      val nodeDescriptor = node.getNodeDescriptor()
      nodeDescriptor is BranchNodeDescriptor.Branch && nodeDescriptor.branchInfo == branchInfo
    } ?: return emptyList()

    return getSelectedRepositories(branchNode)
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