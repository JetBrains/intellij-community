// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ChangeListManager
import javax.swing.tree.DefaultTreeModel

object NoneChangesGroupingPolicy : ChangesGroupingPolicy {
  override fun getParentNodeFor(nodePath: StaticFilePath,
                                node: ChangesBrowserNode<*>,
                                subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? = null
}

object NoneChangesGroupingFactory : ChangesGroupingPolicyFactory() {
  override fun createGroupingPolicy(project: Project, model: DefaultTreeModel): ChangesGroupingPolicy {
    return NoneChangesGroupingPolicy
  }
}

class DefaultChangesGroupingPolicy(val project: Project, val model: DefaultTreeModel) : BaseChangesGroupingPolicy() {
  override fun getParentNodeFor(nodePath: StaticFilePath,
                                node: ChangesBrowserNode<*>,
                                subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? {
    if (isMergeConflict(nodePath, node)) {
      val cachingRoot = getCachingRoot(subtreeRoot, subtreeRoot)
      CONFLICTS_NODE_CACHE[cachingRoot]?.let { return it }

      return ChangesBrowserConflictsNode(project).also {
        it.markAsHelperNode()

        model.insertNodeInto(it, subtreeRoot, subtreeRoot.childCount)
        CONFLICTS_NODE_CACHE[cachingRoot] = it
        TreeModelBuilder.IS_CACHING_ROOT.set(it, true)
      }
    }

    return null
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
    val CONFLICTS_NODE_CACHE = Key.create<ChangesBrowserNode<*>>("ChangesTree.ConflictsNodeCache")
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
