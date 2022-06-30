// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.DIRECTORY_CACHE
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.PATH_NODE_BUILDER
import javax.swing.tree.DefaultTreeModel

class DirectoryChangesGroupingPolicy(val project: Project, val model: DefaultTreeModel) : BaseChangesGroupingPolicy() {
  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*> {
    val grandParent = nextPolicy?.getParentNodeFor(nodePath, subtreeRoot) ?: subtreeRoot
    val cachingRoot = getCachingRoot(grandParent, subtreeRoot)

    return ParentNodeBuilder(subtreeRoot, grandParent, cachingRoot).getParentNodeRecursive(nodePath)
  }

  private inner class ParentNodeBuilder(val subtreeRoot: ChangesBrowserNode<*>,
                                        val grandParent: ChangesBrowserNode<*>,
                                        val cachingRoot: ChangesBrowserNode<*>) {

    fun getParentNodeRecursive(nodePath: StaticFilePath): ChangesBrowserNode<*> {
      generateSequence(nodePath.parent) { it.parent }.forEach { parentPath ->
        val cachedParent = DIRECTORY_CACHE.getValue(cachingRoot)[parentPath.key]
        if (cachedParent != null && cachedParent != grandParent) {
          return cachedParent
        }

        val pathNode = PATH_NODE_BUILDER.getRequired(subtreeRoot).apply(parentPath)
        if (pathNode != null) {
          pathNode.markAsHelperNode()

          val parentNode = cachedParent ?: getParentNodeRecursive(parentPath)
          model.insertNodeInto(pathNode, parentNode, parentNode.childCount)

          DIRECTORY_CACHE.getValue(cachingRoot)[parentPath.key] = pathNode
          return pathNode
        }

        if (cachedParent != null) { // cachedParent == grandParent
          return grandParent
        }
      }

      return grandParent
    }
  }

  class Factory : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(project: Project, model: DefaultTreeModel): DirectoryChangesGroupingPolicy =
      DirectoryChangesGroupingPolicy(project, model)
  }
}