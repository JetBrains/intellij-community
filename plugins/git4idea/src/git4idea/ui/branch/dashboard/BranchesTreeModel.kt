// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ThreeState
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchType
import git4idea.branch.GitRefType
import git4idea.branch.IncomingOutgoingState
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.tree.DefaultMutableTreeNode

internal data class RemoteInfo(val remoteName: String, val repository: GitRepository?)

internal data class BranchInfo(val branch: GitBranch,
                               val isCurrent: Boolean,
                               var isFavorite: Boolean,
                               var incomingOutgoingState: IncomingOutgoingState = IncomingOutgoingState.EMPTY,
                               val repositories: List<GitRepository>) {
  var isMy: ThreeState = ThreeState.UNSURE
  val branchName: @NlsSafe String get() = branch.name
  val isLocalBranch = branch is GitLocalBranch

  override fun toString() = branchName
}

internal sealed class BranchNodeDescriptor {
  abstract val children: List<BranchNodeDescriptor>
  abstract val displayName: String

  internal class Root : BranchNodeDescriptor() {
    override val displayName = "Root"
    override var children: List<BranchNodeDescriptor> = emptyList()

    override fun toString() = "ROOT"
  }

  internal object Head : BranchNodeDescriptor() {
    override val displayName: @Nls String = message("group.Git.HEAD.Branch.Filter.title")
    override val children: List<BranchNodeDescriptor>
      get() = emptyList()

    override fun toString() = "HEAD"
  }

  internal class TopLevelGroup(
    val refType: GitRefType,
    override val children: List<BranchNodeDescriptor>,
  ) : BranchNodeDescriptor() {
    override val displayName: @Nls String = refType.getText()

    override fun toString() = refType.name
  }

  internal class RemoteGroup(val remote: GitRemote, override val children: List<BranchNodeDescriptor>) : BranchNodeDescriptor() {
    override val displayName: String = remote.name

    override fun toString(): String = "REMOTE:$displayName"
  }

  internal class Branch(
    val branchInfo: BranchInfo,
    override val displayName: @NlsSafe String = branchInfo.branchName,
  ) : BranchNodeDescriptor() {
    override val children: List<BranchNodeDescriptor>
      get() = emptyList()

    override fun toString(): String = "BRANCH:${branchInfo.branchName}"
  }

  internal class Repository(val repository: GitRepository, override val children: List<BranchNodeDescriptor>) : BranchNodeDescriptor() {
    override val displayName: @NlsSafe String = DvcsUtil.getShortRepositoryName(repository)

    override fun toString(): String = "REPO:$displayName"
  }

  internal class Group(
    override val displayName: @NlsSafe String,
    override val children: List<BranchNodeDescriptor>,
    val hasFavorites: Boolean,
  ) : BranchNodeDescriptor() {
    override fun toString(): String = "GROUP:$displayName"
  }
}

internal class BranchTreeNode(nodeDescriptor: BranchNodeDescriptor) : DefaultMutableTreeNode(nodeDescriptor) {
  override fun getParent(): BranchTreeNode? = super.parent as BranchTreeNode?

  fun getNodeDescriptor() = userObject as BranchNodeDescriptor

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (other !is BranchTreeNode) return false

    return Objects.equals(this.userObject, other.userObject)
  }

  override fun hashCode() = Objects.hash(userObject)
}

internal class NodeDescriptorsModel(private val rootNode: BranchNodeDescriptor.Root, project: Project) {
  private val incomingOutgoingManager: GitBranchIncomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project)

  fun rebuildFrom(branches: Iterable<BranchInfo>, groupingConfig: Map<GroupingKey, Boolean>) {
    val (localBranches, remoteBranches) = branches.partition { it.isLocalBranch }
    val groupByRepository = groupingConfig[GroupingKey.GROUPING_BY_REPOSITORY]!!
    val groupByPrefix = groupingConfig[GroupingKey.GROUPING_BY_DIRECTORY]!!

    val topLevelGroups = mutableListOf<BranchNodeDescriptor>()
    topLevelGroups += BranchNodeDescriptor.Head
    for ((branches, group) in listOf(localBranches to GitBranchType.LOCAL, remoteBranches to GitBranchType.REMOTE)) {
      if (branches.isNotEmpty()) {
        val tree = groupByRepoAndPrefixIfApplicable(branches, groupByRepository, groupByPrefix)
        topLevelGroups += BranchNodeDescriptor.TopLevelGroup(group, tree)
      }
    }
    rootNode.children = topLevelGroups
  }

  private fun groupByRepoAndPrefixIfApplicable(
    branchesInfo: Iterable<BranchInfo>,
    groupByRepository: Boolean,
    groupByPrefix: Boolean,
  ): List<BranchNodeDescriptor> {
    return if (groupByRepository) {
      val repoToBranch = mutableMapOf<GitRepository, MutableList<BranchInfo>>()
      for (branchInfo in branchesInfo) {
        for (repository in branchInfo.repositories) {
          val incomingOutgoingState =
            if (branchInfo.branch is GitLocalBranch) incomingOutgoingManager.getIncomingOutgoingState(repository, branchInfo.branch)
            else IncomingOutgoingState.EMPTY

          val repoBranch = branchInfo.copy(isCurrent = repository.isCurrentBranch(branchInfo.branchName),
                                           isFavorite = repository.isFavorite(branchInfo),
                                           incomingOutgoingState = incomingOutgoingState)

          repoToBranch.computeIfAbsent(repository) { mutableListOf() }.add(repoBranch)
        }
      }

      val repoNodes = repoToBranch.map { (repository, repoBranches) ->
        val repoChildren = groupByPrefixAndRemoteIfApplicable(repoBranches, groupByPrefix)
        BranchNodeDescriptor.Repository(repository, repoChildren)
      }

      repoNodes.sortedWith(BranchTreeNodeComparator)
    }
    else groupByPrefixAndRemoteIfApplicable(branchesInfo, groupByPrefix)
  }

  private fun groupByPrefixAndRemoteIfApplicable(branchesInfo: Iterable<BranchInfo>,
                                                 groupByPrefix: Boolean): List<BranchNodeDescriptor> {
    val branchesByRemote = mutableMapOf<GitRemote, MutableList<BranchInfo>>()
    val branchesWithoutRemote = mutableListOf<BranchInfo>()

    for (branch in branchesInfo) {
      val remote = (branch.branch as? GitRemoteBranch)?.remote
      if (!groupByPrefix || remote == null) {
        branchesWithoutRemote += branch
      }
      else {
        branchesByRemote.computeIfAbsent(remote) { mutableListOf() }.add(branch)
      }
    }

    val result = mutableListOf<BranchNodeDescriptor>()
    result += groupByPrefixIfApplicable(branchesWithoutRemote, groupByPrefix)
    result += branchesByRemote.map { (remote, remoteBranches) ->
      val remoteChildren = groupByPrefixIfApplicable(remoteBranches, groupByPrefix)
      BranchNodeDescriptor.RemoteGroup(remote, remoteChildren)
    }

    return result.sortedWith(BranchTreeNodeComparator)
  }

  private fun groupByPrefixIfApplicable(branchesInfo: Iterable<BranchInfo>, groupByPrefix: Boolean): List<BranchNodeDescriptor> =
    if (groupByPrefix) groupByPrefix(branchesInfo.map { RefNameSegment(it) })
    else branchesInfo.map { BranchNodeDescriptor.Branch(it) }.sortedWith(BranchTreeNodeComparator)

  private fun groupByPrefix(paths: Iterable<RefNameSegment>): List<BranchNodeDescriptor> {
    val nodes = mutableListOf<BranchNodeDescriptor>()
    val childGroups = mutableMapOf<String, MutableList<RefNameSegment>>()

    for (path in paths) {
      val currentSegment = path.currentSegment()
      if (path.isLastSegment()) {
        nodes.add(BranchNodeDescriptor.Branch(path.refInfo, currentSegment))
      }
      else {
        childGroups.computeIfAbsent(currentSegment) { mutableListOf() }.add(path.apply { move() })
      }
    }

    for ((groupName, childrenPaths) in childGroups) {
      val childrenNodes = groupByPrefix(childrenPaths)
      val hasFavorites = childrenNodes.any { node ->
        node is BranchNodeDescriptor.Branch && node.branchInfo.isFavorite || node is BranchNodeDescriptor.Group && node.hasFavorites
      }
      nodes.add(BranchNodeDescriptor.Group(groupName, childrenNodes, hasFavorites))
    }

    return nodes.sortedWith(BranchTreeNodeComparator)
  }

  private class RefNameSegment(val refInfo: BranchInfo, private var offset: Int = 0) {
    private val path: List<String>

    init {
      val name = if (refInfo.branch is GitRemoteBranch) refInfo.branch.nameForRemoteOperations else refInfo.branch.name
      path = name.split("/")
    }

    fun isLastSegment() = offset == path.lastIndex
    fun currentSegment() = path[offset]
    fun move() {
      offset++
    }
  }

  private fun GitRepository.isCurrentBranch(branchName: String) = currentBranch?.name == branchName
  private fun GitRepository.isFavorite(branch: BranchInfo) =
    project.service<GitBranchManager>().isFavorite(if (branch.isLocalBranch) GitBranchType.LOCAL else GitBranchType.REMOTE,
                                                   this, branch.branchName)
}
