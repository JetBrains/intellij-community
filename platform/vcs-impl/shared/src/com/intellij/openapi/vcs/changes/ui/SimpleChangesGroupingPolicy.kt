// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.util.NotNullLazyKey
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultTreeModel

abstract class SimpleChangesGroupingPolicy<T : Any>(val model: DefaultTreeModel) : BaseChangesGroupingPolicy() {
  final override fun getParentNodeFor(
    nodePath: StaticFilePath,
    node: ChangesBrowserNode<*>,
    subtreeRoot: ChangesBrowserNode<*>,
  ): ChangesBrowserNode<*>? {
    val nextPolicyGroup = nextPolicy?.getParentNodeFor(nodePath, node, subtreeRoot)
    val grandParent = nextPolicyGroup ?: subtreeRoot
    val cachingRoot = getCachingRoot(grandParent, subtreeRoot)

    val groupNodeValue = getGroupRootValueFor(nodePath, node)
    if (groupNodeValue == null) return nextPolicyGroup

    GROUP_NODE_CACHE.getValue(cachingRoot)[groupNodeValue]?.let { return it }

    val groupNode = createGroupRootNode(groupNodeValue) ?: return null
    model.insertNodeInto(groupNode, grandParent, grandParent.childCount)

    markCachingRoot(groupNode)
    GROUP_NODE_CACHE.getValue(cachingRoot)[groupNodeValue] = groupNode

    if (groupNode is ChangesBrowserNode.NodeWithFilePath) {
      val staticFilePath = StaticFilePath(groupNode.nodeFilePath)
      TreeModelBuilderKeys.DIRECTORY_CACHE.getValue(groupNode)[staticFilePath.key] = groupNode
    }

    return groupNode
  }

  /**
   * The return value is also used as a key to find existing group node
   * Consider not using common types ([Boolean], [com.intellij.openapi.vcs.FilePath]) to avoid clashes with other grouping policies.
   */
  protected abstract fun getGroupRootValueFor(nodePath: StaticFilePath, node: ChangesBrowserNode<*>): T?

  /**
   * The node may implement [ChangesBrowserNode.NodeWithFilePath] if the grouping root has a well-defined path,
   * and its children are typically located under it.
   */
  protected abstract fun createGroupRootNode(value: T): ChangesBrowserNode<*>?

  companion object {
    @JvmField
    @ApiStatus.Internal
    val GROUP_NODE_CACHE: NotNullLazyKey<MutableMap<Any, ChangesBrowserNode<*>>, ChangesBrowserNode<*>> =
      NotNullLazyKey.createLazyKey("SimpleChangesGroupingPolicy.GroupNodeCache") { HashMap() }
  }
}
