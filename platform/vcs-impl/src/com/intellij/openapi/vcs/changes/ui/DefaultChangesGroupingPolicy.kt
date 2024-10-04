// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ChangeListManager
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultTreeModel

@ApiStatus.Internal
object NoneChangesGroupingPolicy : ChangesGroupingPolicy {
  override fun getParentNodeFor(nodePath: StaticFilePath,
                                node: ChangesBrowserNode<*>,
                                subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? = null
}

object NoneChangesGroupingFactory : ChangesGroupingPolicyFactory() {
  override fun createGroupingPolicy(project: Project, model: DefaultTreeModel): ChangesGroupingPolicy =
    NoneChangesGroupingPolicy
}

@ApiStatus.Internal
class DefaultChangesGroupingPolicy(val project: Project, model: DefaultTreeModel) : SimpleChangesGroupingPolicy<Any>(model) {
  override fun getGroupRootValueFor(nodePath: StaticFilePath, node: ChangesBrowserNode<*>): Any? {
    if (isMergeConflict(nodePath, node)) {
      return CONFLICTS_NODE_FLAG
    }
    return null
  }

  override fun createGroupRootNode(value: Any): ChangesBrowserNode<*> {
    assert(value == CONFLICTS_NODE_FLAG)
    val conflictsRoot = ChangesBrowserConflictsNode(project)
    conflictsRoot.markAsHelperNode()
    return conflictsRoot
  }

  private fun isMergeConflict(nodePath: StaticFilePath, node: ChangesBrowserNode<*>): Boolean {
    if (node is ChangesGroupingPolicy.CompatibilityPlaceholderChangesBrowserNode) {
      val vFile = nodePath.resolve() ?: return false
      val status = ChangeListManager.getInstance(project).getStatus(vFile)
      return isMergeConflict(status)
    }

    if (node is ChangesBrowserChangeNode) {
      return isMergeConflict(node.userObject.fileStatus)
    }
    return false
  }

  private fun isMergeConflict(status: FileStatus): Boolean {
    return status === FileStatus.MERGED_WITH_CONFLICTS ||
           status === FileStatus.MERGED_WITH_BOTH_CONFLICTS ||
           status === FileStatus.MERGED_WITH_PROPERTY_CONFLICTS
  }

  companion object {
    private val CONFLICTS_NODE_FLAG = Any()
  }

  internal class Factory @JvmOverloads constructor(private val forLocalChanges: Boolean = false) : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(project: Project, model: DefaultTreeModel): ChangesGroupingPolicy {
      return when {
        forLocalChanges -> DefaultChangesGroupingPolicy(project, model)
        else -> NoneChangesGroupingPolicy
      }
    }
  }
}
