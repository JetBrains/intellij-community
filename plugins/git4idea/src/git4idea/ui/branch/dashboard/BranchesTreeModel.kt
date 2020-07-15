// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.util.ThreeState
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRepository
import java.util.*
import javax.swing.tree.DefaultMutableTreeNode

internal val GIT_BRANCHES = DataKey.create<Set<BranchInfo>>("GitBranchKey")

internal data class BranchInfo(val branchName: String,
                               val isLocal: Boolean,
                               val isCurrent: Boolean,
                               var isFavorite: Boolean,
                               val repositories: List<GitRepository>) {
  var isMy: ThreeState = ThreeState.UNSURE
  override fun toString() = branchName
}

internal data class BranchNodeDescriptor(val type: NodeType,
                                         val branchInfo: BranchInfo? = null,
                                         val displayName: String? = branchInfo?.branchName,
                                         val parent: BranchNodeDescriptor? = null) {
  override fun toString(): String {
    val suffix = branchInfo?.branchName ?: displayName
    return if (suffix != null) "$type:$suffix" else "$type"
  }

  fun getDisplayText() = displayName ?: branchInfo?.branchName
}

internal enum class NodeType {
  ROOT, LOCAL_ROOT, REMOTE_ROOT, BRANCH, GROUP_NODE
}

internal class BranchTreeNode(nodeDescriptor: BranchNodeDescriptor) : DefaultMutableTreeNode(nodeDescriptor) {

  fun getTextRepresentation(): String {
    val nodeDescriptor = userObject as? BranchNodeDescriptor ?: return super.toString()
    return when (nodeDescriptor.type) {
      NodeType.LOCAL_ROOT -> message("group.Git.Local.Branch.title")
      NodeType.REMOTE_ROOT -> message("group.Git.Remote.Branch.title")
      else -> nodeDescriptor.getDisplayText() ?: super.toString()
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

  fun populateFrom(branches: Sequence<BranchInfo>, useGrouping: Boolean) {
    branches.forEach { branch -> populateFrom(branch, useGrouping) }
  }

  private fun populateFrom(br: BranchInfo, useGrouping: Boolean) {
    val branch = with(br) { BranchInfo(branchName, isLocal, isCurrent, isFavorite, repositories) }
    var curParent: BranchNodeDescriptor = if (branch.isLocal) localRootNodeDescriptor else remoteRootNodeDescriptor

    if (!useGrouping) {
      addChild(curParent, BranchNodeDescriptor(NodeType.BRANCH, branch, parent = curParent))
      return
    }

    val iter = branch.branchName.split("/").iterator()

    while (iter.hasNext()) {
      val branchNamePart = iter.next()
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