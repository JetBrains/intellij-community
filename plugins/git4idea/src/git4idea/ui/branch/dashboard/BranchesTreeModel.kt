// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.util.ThreeState
import git4idea.repo.GitRepository
import java.util.*
import javax.swing.tree.DefaultMutableTreeNode

internal val GIT_BRANCHES = DataKey.create<Set<BranchInfo>>("GitBranchKey")

internal data class BranchInfo(val branchName: String,
                               val isLocal: Boolean,
                               val isCurrent: Boolean,
                               val repositories: List<GitRepository>) {
  var isMy: ThreeState = ThreeState.UNSURE
  var isFavorite = false
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
}

internal enum class NodeType {
  ROOT, LOCAL_ROOT, REMOTE_ROOT, BRANCH, GROUP_NODE
}

internal class BranchTreeNode(nodeDescriptor: BranchNodeDescriptor) : DefaultMutableTreeNode(nodeDescriptor) {

  fun getTextRepresentation(): String {
    val nodeDescriptor = userObject as? BranchNodeDescriptor ?: return super.toString()
    return when (nodeDescriptor.type) {
      NodeType.ROOT -> "root"
      NodeType.LOCAL_ROOT -> "Local"
      NodeType.REMOTE_ROOT -> "Remote"
      else -> nodeDescriptor.displayName ?: nodeDescriptor.branchInfo?.branchName ?: super.toString()
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

internal fun BranchInfo.toNodeDescriptors(useGrouping: Boolean): List<BranchNodeDescriptor> {
  if (!useGrouping) {
    return listOf(BranchNodeDescriptor(NodeType.BRANCH, this))
  }

  val result = arrayListOf<BranchNodeDescriptor>()
  var curParent: BranchNodeDescriptor? = null
  val iter = branchName.split("/").iterator()

  while (iter.hasNext()) {
    val branchNamePart = iter.next()
    val groupNode = iter.hasNext()
    val nodeType = if (groupNode) NodeType.GROUP_NODE else NodeType.BRANCH
    val branchInfo = if (nodeType == NodeType.BRANCH) this else null

    curParent = BranchNodeDescriptor(nodeType, branchInfo, displayName = branchNamePart, parent = curParent)
    result.add(curParent)
  }
  return result
}

internal fun Set<BranchInfo>.toNodeDescriptors(useGrouping: Boolean) =
  asSequence()
    .flatMap { it.toNodeDescriptors(useGrouping).asSequence() }
    .distinct()
    .sortedWith(BRANCH_TREE_NODE_COMPARATOR)
    .partition { it.type == NodeType.GROUP_NODE }

internal val BRANCH_TREE_NODE_COMPARATOR = Comparator<BranchNodeDescriptor> { d1, d2 ->
  val b1 = d1.branchInfo
  val b2 = d2.branchInfo
  val displayName1 = d1.displayName
  val displayName2 = d2.displayName
  val isGroupNodes = d1.type == NodeType.GROUP_NODE && d2.type == NodeType.GROUP_NODE
  when {
    b1 == null || b2 == null ->
      if (isGroupNodes && displayName1 != null && displayName2 != null) displayName1.compareTo(displayName2) else d1.type.compareTo(d2.type)
    b1.isCurrent && !b2.isCurrent -> -1
    !b1.isCurrent && b2.isCurrent -> 1
    b1.isFavorite && !b2.isFavorite -> -1
    !b1.isFavorite && b2.isFavorite -> 1
    b1.isLocal && !b2.isLocal -> -1
    !b1.isLocal && b2.isLocal -> 1
    else -> {
      b1.branchName.compareTo(b2.branchName)
    }
  }
}