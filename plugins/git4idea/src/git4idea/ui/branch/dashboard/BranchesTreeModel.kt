// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.dvcs.branch.GroupingKey.GROUPING_BY_DIRECTORY
import com.intellij.dvcs.branch.GroupingKey.GROUPING_BY_REPOSITORY
import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ThreeState
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchType
import git4idea.branch.IncomingOutgoingState
import git4idea.i18n.GitBundle.message
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

internal data class BranchNodeDescriptor(
  val type: NodeType,
  val branchInfo: BranchInfo? = null,
  val repository: GitRepository? = null,
  val displayName: @Nls String? = resolveDisplayName(branchInfo, repository),
  var parent: BranchNodeDescriptor? = null,
) {
  override fun toString(): String {
    val suffix = branchInfo?.branchName ?: displayName
    return if (suffix != null) "$type:$suffix" else "$type"
  }
}

private fun resolveDisplayName(
  branchInfo: BranchInfo?,
  repository: GitRepository?,
) = when {
  branchInfo != null -> branchInfo.branchName
  repository != null -> DvcsUtil.getShortRepositoryName(repository)
  else -> null
}

internal enum class NodeType {
  ROOT, LOCAL_ROOT, REMOTE_ROOT, BRANCH, GROUP_NODE, GROUP_REPOSITORY_NODE, HEAD_NODE
}

internal class BranchTreeNode(nodeDescriptor: BranchNodeDescriptor) : DefaultMutableTreeNode(nodeDescriptor) {

  fun getTextRepresentation(): @Nls String {
    val nodeDescriptor = userObject as? BranchNodeDescriptor ?: return super.toString() //NON-NLS
    return when (nodeDescriptor.type) {
      NodeType.LOCAL_ROOT -> message("group.Git.Local.Branch.title")
      NodeType.REMOTE_ROOT -> message("group.Git.Remote.Branch.title")
      NodeType.HEAD_NODE -> message("group.Git.HEAD.Branch.Filter.title")
      else -> nodeDescriptor.displayName ?: super.toString() //NON-NLS
    }
  }

  fun getNodeDescriptor() = userObject as BranchNodeDescriptor

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (other !is BranchTreeNode) return false

    return Objects.equals(this.userObject, other.userObject)
  }

  override fun hashCode() = Objects.hash(userObject)
}

internal class NodeDescriptorsModel(
  private val localRootNodeDescriptor: BranchNodeDescriptor,
  private val remoteRootNodeDescriptor: BranchNodeDescriptor,
) {
  var localNodeExist = false
    private set
  var remoteNodeExist = false
    private set

  /**
   * Parent node descriptor to direct children map
   */
  private val branchNodeDescriptors = hashMapOf<BranchNodeDescriptor, MutableSet<BranchNodeDescriptor>>()


  fun getChildrenForParent(parent: BranchNodeDescriptor): Set<BranchNodeDescriptor> =
    branchNodeDescriptors.getOrDefault(parent, emptySet())

  fun reloadFrom(
    localBranches: Collection<BranchInfo>,
    remoteBranches: Collection<BranchInfo>,
    filter: (BranchInfo) -> Boolean,
    groupingConfig: Map<GroupingKey, Boolean>,
  ) {
    clear()

    localNodeExist = localBranches.isNotEmpty()
    remoteNodeExist = remoteBranches.isNotEmpty()

    val branches = (localBranches.asSequence() + remoteBranches.asSequence()).filter(filter)

    branches.forEach { branch -> populateFrom(branch, groupingConfig) }
    branchNodeDescriptors.forEach { (parent, children) ->
      children.forEach { it.parent = parent }
    }
  }

  private fun clear() = branchNodeDescriptors.clear()

  private fun populateFrom(br: BranchInfo, groupingConfig: Map<GroupingKey, Boolean>) {
    val curParent: BranchNodeDescriptor = if (br.isLocalBranch) localRootNodeDescriptor else remoteRootNodeDescriptor
    val groupByDirectory = groupingConfig[GROUPING_BY_DIRECTORY]!!
    val groupByRepository = groupingConfig[GROUPING_BY_REPOSITORY]!!

    when {
      groupByRepository && groupByDirectory -> {
        applyGroupingByRepository(curParent, br) { branch, parent -> applyGroupingByDirectory(parent, branch) }
      }
      groupByRepository -> applyGroupingByRepository(curParent, br)
      groupByDirectory -> applyGroupingByDirectory(curParent, br.copy())
      else -> addChild(curParent, BranchNodeDescriptor(NodeType.BRANCH, br.copy(), parent = curParent))
    }
  }

  private fun applyGroupingByRepository(
    curParent: BranchNodeDescriptor,
    br: BranchInfo,
    additionalGrouping: ((BranchInfo, BranchNodeDescriptor) -> Unit)? = null,
  ) {
    val repositoryNodeDescriptors = hashMapOf<GitRepository, BranchNodeDescriptor>()
    br.repositories.forEach { repository ->
      val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(repository.project)

      val branch = br.copy(isCurrent = repository.isCurrentBranch(br.branchName),
                           isFavorite = repository.isFavorite(br),
                           incomingOutgoingState = incomingOutgoingManager.getIncomingOutgoingState(repository, GitLocalBranch(br.branchName)))

      val repositoryNodeDescriptor = repositoryNodeDescriptors.computeIfAbsent(repository) {
        val repositoryNodeDescriptor = BranchNodeDescriptor(NodeType.GROUP_REPOSITORY_NODE, repository = repository, parent = curParent)
        addChild(curParent, repositoryNodeDescriptor)
        repositoryNodeDescriptor
      }

      if (additionalGrouping != null) {
        additionalGrouping.invoke(branch, repositoryNodeDescriptor)
      }
      else {
        val branchNodeDescriptor = BranchNodeDescriptor(NodeType.BRANCH, branch, parent = repositoryNodeDescriptor)
        addChild(repositoryNodeDescriptor, branchNodeDescriptor)
      }
    }
  }

  private fun applyGroupingByDirectory(parent: BranchNodeDescriptor, branch: BranchInfo) {
    val iter = branch.branchName.split("/").iterator()
    var curParent = parent

    while (iter.hasNext()) {
      @NlsSafe val branchNamePart = iter.next()
      val groupNode = iter.hasNext()
      val nodeType = if (groupNode) NodeType.GROUP_NODE else NodeType.BRANCH
      val branchInfo = if (nodeType == NodeType.BRANCH) branch else null

      val branchNodeDescriptor = BranchNodeDescriptor(nodeType, branchInfo, displayName = branchNamePart, parent = curParent)
      addChild(curParent, branchNodeDescriptor)
      curParent = branchNodeDescriptor
    }
  }

  private fun addChild(parent: BranchNodeDescriptor, child: BranchNodeDescriptor) {
    val directChildren = branchNodeDescriptors.computeIfAbsent(parent) { sortedSetOf(BranchTreeNodeComparator) }
    directChildren.add(child)
    branchNodeDescriptors[parent] = directChildren
  }

  private fun GitRepository.isCurrentBranch(branchName: String) = currentBranch?.name == branchName
  private fun GitRepository.isFavorite(branch: BranchInfo) =
    project.service<GitBranchManager>().isFavorite(if (branch.isLocalBranch) GitBranchType.LOCAL else GitBranchType.REMOTE,
                                                   this, branch.branchName)
}
