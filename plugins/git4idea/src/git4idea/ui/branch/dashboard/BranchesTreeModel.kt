// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.dvcs.branch.GroupingKey.GROUPING_BY_DIRECTORY
import com.intellij.dvcs.branch.GroupingKey.GROUPING_BY_REPOSITORY
import com.intellij.dvcs.ui.BranchActionGroup
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ThreeState
import git4idea.branch.GitBranchType
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.dashboard.BranchesDashboardUtil.getIncomingOutgoingState
import icons.DvcsImplIcons
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.Icon
import javax.swing.tree.DefaultMutableTreeNode

internal val GIT_BRANCHES = DataKey.create<List<BranchInfo>>("GitBranchKey")
internal val GIT_BRANCH_FILTERS = DataKey.create<List<String>>("GitBranchFilterKey")

internal data class RemoteInfo(val remoteName: String, val repository: GitRepository?)

internal data class BranchInfo(val branchName: @NlsSafe String,
                               val isLocal: Boolean,
                               val isCurrent: Boolean,
                               var isFavorite: Boolean,
                               var incomingOutgoingState: IncomingOutgoing? = null,
                               val repositories: List<GitRepository>) {
  var isMy: ThreeState = ThreeState.UNSURE
  override fun toString() = branchName
}

internal enum class IncomingOutgoing {
  INCOMING, OUTGOING, INCOMING_AND_OUTGOING;

  fun hasIncoming() = this == INCOMING || this == INCOMING_AND_OUTGOING
  fun hasOutgoing() = this == OUTGOING || this == INCOMING_AND_OUTGOING

  val icon: Icon
    get() = when (this) {
      INCOMING -> DvcsImplIcons.Incoming
      OUTGOING -> DvcsImplIcons.Outgoing
      INCOMING_AND_OUTGOING -> BranchActionGroup.getIncomingOutgoingIcon()
    }
}

internal data class BranchNodeDescriptor(val type: NodeType,
                                         val branchInfo: BranchInfo? = null,
                                         val repository: GitRepository? = null,
                                         val displayName: @Nls String? = resolveDisplayName(branchInfo, repository),
                                         val parent: BranchNodeDescriptor? = null) {
  override fun toString(): String {
    val suffix = branchInfo?.branchName ?: displayName
    return if (suffix != null) "$type:$suffix" else "$type"
  }

  fun getDisplayText() = displayName ?: branchInfo?.branchName
}

private fun resolveDisplayName(branchInfo: BranchInfo?,
                               repository: GitRepository?) = when {
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
      NodeType.GROUP_REPOSITORY_NODE -> " ${nodeDescriptor.getDisplayText()}"
      else -> nodeDescriptor.getDisplayText() ?: super.toString() //NON-NLS
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

internal class NodeDescriptorsModel(private val localRootNodeDescriptor: BranchNodeDescriptor,
                                    private val remoteRootNodeDescriptor: BranchNodeDescriptor) {
  /**
   * Parent node descriptor to direct children map
   */
  private val branchNodeDescriptors = hashMapOf<BranchNodeDescriptor, MutableSet<BranchNodeDescriptor>>()

  fun clear() = branchNodeDescriptors.clear()

  fun getChildrenForParent(parent: BranchNodeDescriptor): Set<BranchNodeDescriptor> =
    branchNodeDescriptors.getOrDefault(parent, emptySet())

  fun populateFrom(branches: Sequence<BranchInfo>, groupingConfig: Map<GroupingKey, Boolean>) {
    branches.forEach { branch -> populateFrom(branch, groupingConfig) }
  }

  private fun populateFrom(br: BranchInfo, groupingConfig: Map<GroupingKey, Boolean>) {
    val curParent: BranchNodeDescriptor = if (br.isLocal) localRootNodeDescriptor else remoteRootNodeDescriptor
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

  private fun applyGroupingByRepository(curParent: BranchNodeDescriptor,
                                        br: BranchInfo,
                                        additionalGrouping: ((BranchInfo, BranchNodeDescriptor) -> Unit)? = null) {
    val repositoryNodeDescriptors = hashMapOf<GitRepository, BranchNodeDescriptor>()

    br.repositories.forEach { repository ->
      val branch = br.copy(isCurrent = repository.isCurrentBranch(br.branchName),
                           isFavorite = repository.isFavorite(br),
                           incomingOutgoingState = repository.getIncomingOutgoingState(br.branchName))

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
    val directChildren = branchNodeDescriptors.computeIfAbsent(parent) { sortedSetOf(BRANCH_TREE_NODE_COMPARATOR) }
    directChildren.add(child)
    branchNodeDescriptors[parent] = directChildren
  }

  private fun GitRepository.isCurrentBranch(branchName: String) = currentBranch?.name == branchName
  private fun GitRepository.isFavorite(branch: BranchInfo) =
    project.service<GitBranchManager>().isFavorite(if (branch.isLocal) GitBranchType.LOCAL else GitBranchType.REMOTE,
                                                   this, branch.branchName)
}

internal val BRANCH_TREE_NODE_COMPARATOR = Comparator<BranchNodeDescriptor> { d1, d2 ->
  val b1 = d1.branchInfo
  val b2 = d2.branchInfo
  val displayText1 = d1.getDisplayText()
  val displayText2 = d2.getDisplayText()
  val b1GroupNode = d1.type == NodeType.GROUP_NODE
  val b2GroupNode = d2.type == NodeType.GROUP_NODE
  val b1Current = b1 != null && b1.isCurrent
  val b2Current = b2 != null && b2.isCurrent
  val b1Favorite = b1 != null && b1.isFavorite
  val b2Favorite = b2 != null && b2.isFavorite
  fun compareByDisplayTextOrType() =
    if (displayText1 != null && displayText2 != null) displayText1.compareTo(displayText2) else d1.type.compareTo(d2.type)

  when {
    b1Current && b2Current -> compareByDisplayTextOrType()
    b1Current -> -1
    b2Current -> 1
    b1Favorite && b2Favorite -> compareByDisplayTextOrType()
    b1Favorite -> -1
    b2Favorite -> 1
    b1GroupNode && b2GroupNode -> compareByDisplayTextOrType()
    b1GroupNode -> -1
    b2GroupNode -> 1
    else -> compareByDisplayTextOrType()
  }
}
