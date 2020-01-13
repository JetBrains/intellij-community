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

internal data class BranchNodeDescriptor(val type: NodeType, val branchInfo: BranchInfo? = null)

internal enum class NodeType {
  ROOT, LOCAL_ROOT, REMOTE_ROOT, BRANCH
}

internal class BranchTreeNode(nodeDescriptor: BranchNodeDescriptor) : DefaultMutableTreeNode(nodeDescriptor) {

  fun getTextRepresentation(): String {
    val nodeDescriptor = userObject as? BranchNodeDescriptor ?: return super.toString()
    return when (nodeDescriptor.type) {
      NodeType.ROOT -> "root"
      NodeType.LOCAL_ROOT -> "Local"
      NodeType.REMOTE_ROOT -> "Remote"
      else -> nodeDescriptor.branchInfo?.branchName ?: super.toString()
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

internal fun Set<BranchInfo>.toNodeDescriptors() =
  asSequence()
    .map { BranchNodeDescriptor(NodeType.BRANCH, it) }
    .sortedWith(BRANCH_TREE_NODE_COMPARATOR)
    .toList()

internal val BRANCH_TREE_NODE_COMPARATOR = Comparator<BranchNodeDescriptor> { d1, d2 ->
  val b1 = d1.branchInfo
  val b2 = d2.branchInfo
  when {
    b1 == null || b2 == null -> d1.type.compareTo(d2.type)
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