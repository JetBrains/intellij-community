// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.DIRECTORY_CACHE
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.PATH_NODE_BUILDER
import javax.swing.tree.DefaultTreeModel

class DirectoryChangesGroupingPolicy(val project: Project, val model: DefaultTreeModel) : ChangesGroupingPolicy {
  private val innerPolicy = ChangesGroupingPolicyFactory.getInstance(project)?.createGroupingPolicy(model)

  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*> {
    DIRECTORY_POLICY.set(subtreeRoot, this)

    innerPolicy?.getParentNodeFor(nodePath, subtreeRoot)?.let {
      it.markAsHelperNode()
      return it
    }

    return getParentNodeInternal(nodePath, subtreeRoot)
  }

  @JvmName("getParentNodeInternal")
  internal fun getParentNodeInternal(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*> {
    generateSequence(nodePath.parent) { it.parent }.forEach { parentPath ->
      DIRECTORY_CACHE.getValue(subtreeRoot)[parentPath.key]?.let { return it }

      PATH_NODE_BUILDER.getRequired(subtreeRoot).apply(parentPath)?.let {
        it.markAsHelperNode()

        val grandParent = getParentNodeFor(parentPath, subtreeRoot)
        model.insertNodeInto(it, grandParent, grandParent.childCount)
        DIRECTORY_CACHE.getValue(subtreeRoot)[parentPath.key] = it
        return it
      }
    }

    return subtreeRoot
  }

  class Factory(val project: Project) : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(model: DefaultTreeModel) = DirectoryChangesGroupingPolicy(project, model)
  }

  companion object {
    @JvmField internal val DIRECTORY_POLICY = Key.create<DirectoryChangesGroupingPolicy>("ChangesTree.DirectoryPolicy")
  }
}