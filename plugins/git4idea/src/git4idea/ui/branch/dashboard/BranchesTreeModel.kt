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

internal object BranchTreeNodes {
  val rootNode = BranchTreeNode(BranchNodeDescriptor(NodeType.ROOT))
  val localBranchesNode = BranchTreeNode(BranchNodeDescriptor(NodeType.LOCAL_ROOT))
  val remoteBranchesNode = BranchTreeNode(BranchNodeDescriptor(NodeType.REMOTE_ROOT))
}

internal fun getRootNodeDescriptors(localNodeExist: Boolean, remoteNodeExist: Boolean) =
  mutableListOf<BranchNodeDescriptor>().apply {
    if (localNodeExist) add(BranchTreeNodes.localBranchesNode.getNodeDescriptor())
    if (remoteNodeExist) add(BranchTreeNodes.remoteBranchesNode.getNodeDescriptor())
  }

internal fun Set<BranchInfo>.toNodeDescriptors() =
  asSequence()
    .map { BranchNodeDescriptor(NodeType.BRANCH, it) }
    .sortedWith(BRANCH_TREE_NODE_COMPARATOR)
    .toList()
