// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
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
        DIRECTORY_CACHE.getValue(cachingRoot)[parentPath.key]?.let {
          if (grandParent == it) {
            GRAND_PARENT_CANDIDATE.set(subtreeRoot, it)
            try {
              return getPathNode(parentPath) ?: it
            }
            finally {
              GRAND_PARENT_CANDIDATE.set(subtreeRoot, null)
            }
          }
          return it
        }

        getPathNode(parentPath)?.let { return it }
      }

      return grandParent
    }

    private fun getPathNode(nodePath: StaticFilePath): ChangesBrowserNode<*>? {
      return createPathNode(nodePath)?.let { pathNode ->
        val parentNode = GRAND_PARENT_CANDIDATE.get(subtreeRoot) ?: getParentNodeRecursive(nodePath)
        model.insertNodeInto(pathNode, parentNode, parentNode.childCount)
        pathNode
      }
    }

    private fun createPathNode(nodePath: StaticFilePath): ChangesBrowserNode<*>? {
      val pathNode = PATH_NODE_BUILDER.getRequired(subtreeRoot).apply(nodePath) ?: return null
      pathNode.markAsHelperNode()

      DIRECTORY_CACHE.getValue(cachingRoot)[nodePath.key] = pathNode
      return pathNode
    }
  }

  class Factory : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(project: Project, model: DefaultTreeModel): DirectoryChangesGroupingPolicy =
      DirectoryChangesGroupingPolicy(project, model)
  }

  companion object {
    internal val GRAND_PARENT_CANDIDATE = Key.create<ChangesBrowserNode<*>?>("ChangesTree.GrandParentCandidate")
  }
}