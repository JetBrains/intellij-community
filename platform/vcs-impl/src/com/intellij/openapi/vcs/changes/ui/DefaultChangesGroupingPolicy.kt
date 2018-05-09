// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ChangeListManager
import javax.swing.tree.DefaultTreeModel

object NoneChangesGroupingPolicy : ChangesGroupingPolicy {
  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? = null
}

class DefaultChangesGroupingPolicy(val project: Project, val model: DefaultTreeModel) : BaseChangesGroupingPolicy() {
  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? {
    val vFile = nodePath.resolve() ?: return null
    val status = ChangeListManager.getInstance(project).getStatus(vFile)
    if (status == FileStatus.MERGED_WITH_CONFLICTS) {
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

  companion object {
    val CONFLICTS_NODE_CACHE = Key.create<ChangesBrowserNode<*>>("ChangesTree.ConflictsNodeCache")
  }

  class Factory @JvmOverloads constructor(val project: Project, val forLocalChanges: Boolean = false) : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(model: DefaultTreeModel) =
      if (forLocalChanges) DefaultChangesGroupingPolicy(project, model) else NoneChangesGroupingPolicy
  }
}
