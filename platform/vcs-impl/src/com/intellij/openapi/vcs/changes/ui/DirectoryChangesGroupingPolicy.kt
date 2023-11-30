// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.DIRECTORY_CACHE
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.PATH_NODE_BUILDER
import java.util.function.Function
import javax.swing.tree.DefaultTreeModel

class DirectoryChangesGroupingPolicy(val project: Project, val model: DefaultTreeModel) : BaseChangesGroupingPolicy() {
  override fun getParentNodeFor(nodePath: StaticFilePath,
                                node: ChangesBrowserNode<*>,
                                subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*> {
    val nextPolicyGroup = nextPolicy?.getParentNodeFor(nodePath, node, subtreeRoot)
    val grandParent = nextPolicyGroup ?: subtreeRoot
    val cachingRoot = getCachingRoot(grandParent, subtreeRoot)
    val pathBuilder = PATH_NODE_BUILDER.getRequired(subtreeRoot)

    return getParentNodeRecursive(nodePath, pathBuilder, grandParent, cachingRoot)
  }

  private fun getParentNodeRecursive(nodePath: StaticFilePath,
                                     pathBuilder: Function<StaticFilePath, ChangesBrowserNode<*>?>,
                                     grandParent: ChangesBrowserNode<*>,
                                     cachingRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*> {
    var cachedParent: ChangesBrowserNode<*>? = null
    val nodes = mutableListOf<ChangesBrowserNode<*>>()

    // for(var parentPath = nodePath.parent; parentPath != null; parentPath = parentPath.parent)
    var parentPath: StaticFilePath = nodePath
    while (true) {
      parentPath = parentPath.parent ?: break

      cachedParent = DIRECTORY_CACHE.getValue(cachingRoot)[parentPath.key]
      if (cachedParent != null) {
        break
      }

      val pathNode = pathBuilder.apply(parentPath)
      if (pathNode != null) {
        pathNode.markAsHelperNode()
        DIRECTORY_CACHE.getValue(cachingRoot)[parentPath.key] = pathNode
        nodes.add(pathNode)
      }
    }

    var node = cachedParent ?: grandParent
    for (nextNode in nodes.asReversed()) {
      model.insertNodeInto(nextNode, node, node.childCount)
      node = nextNode
    }
    return node
  }

  class Factory : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(project: Project, model: DefaultTreeModel): DirectoryChangesGroupingPolicy =
      DirectoryChangesGroupingPolicy(project, model)
  }
}