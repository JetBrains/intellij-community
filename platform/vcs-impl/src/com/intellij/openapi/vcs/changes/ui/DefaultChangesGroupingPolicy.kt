// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.merge.MergeConflictManager
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
    if (!MergeConflictManager.isNonModalMergeEnabled(project)) return null

    if (isMergeStatus(nodePath, node)) {
      return RESOLVED_CONFLICTS_NODE_FLAG
    }
    return null
  }

  override fun createGroupRootNode(value: Any): ChangesBrowserNode<*> {
    assert(value == RESOLVED_CONFLICTS_NODE_FLAG)
    val resolvedConflictsRoot = ChangesBrowserResolvedConflictsNode(project)
    resolvedConflictsRoot.markAsHelperNode()
    return resolvedConflictsRoot
  }

  private fun isMergeStatus(nodePath: StaticFilePath, node: ChangesBrowserNode<*>): Boolean {
    if (node is ChangesGroupingPolicy.CompatibilityPlaceholderChangesBrowserNode) {
      val vFile = nodePath.resolve() ?: return false
      val status = ChangeListManager.getInstance(project).getStatus(vFile)

      return status == FileStatus.MERGE || MergeConflictManager.getInstance(project).isResolvedConflict(nodePath.filePath)
    }

    if (node is ChangesBrowserChangeNode) {
      val change = node.userObject
      val filePath = ChangesUtil.getFilePath(change)

      return change.fileStatus == FileStatus.MERGE
             || (MergeConflictManager.getInstance(project).isResolvedConflict(filePath))

    }
    val userObject = node.userObject
    if (userObject is FilePath) {
      return MergeConflictManager.getInstance(project).isResolvedConflict(userObject)
    }
    return false
  }

  companion object {
    private val RESOLVED_CONFLICTS_NODE_FLAG = Any()
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
