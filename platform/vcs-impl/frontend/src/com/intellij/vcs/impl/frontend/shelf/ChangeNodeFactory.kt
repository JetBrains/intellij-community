// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf

import com.intellij.ui.SimpleTextAttributes
import com.intellij.vcs.impl.frontend.shelf.tree.*
import com.intellij.vcs.impl.shared.rhizome.*
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.reflect.KClass

private val converters = listOf(ShelvedChangeListNodeConverter(), RootNodeConverter(), TagNodeConverter(), ShelvedChangeNodeConverter())


fun <T : NodeEntity> T.convertToTreeNode(): ChangesBrowserNode<*>? {
  @Suppress("UNCHECKED_CAST")
  val converter = converters.firstOrNull { it.isNodeAcceptable(this) } as? EntityNodeConverter<T, ChangesBrowserNode<*>> ?: return null
  return converter.convert(this)
}

fun <T : NodeEntity> T.convertToTreeNodeRecursive(): ChangesBrowserNode<*>? {
  return dfs(this, null)
}

private fun dfs(node: NodeEntity?, parent: DefaultMutableTreeNode?): ChangesBrowserNode<*>? {
  if (node == null) return null
  val treeNode: ChangesBrowserNode<*> = node.convertToTreeNode() ?: return null
  parent?.add(treeNode)
  for (child in node.children.sortedBy { it.orderInParent }) {
    dfs(child, treeNode)
  }
  return treeNode
}

abstract class EntityNodeConverter<E : NodeEntity, N : ChangesBrowserNode<*>>(val acceptedClass: KClass<E>) {
  abstract fun convert(entity: E): N

  fun isNodeAcceptable(node: NodeEntity): Boolean = acceptedClass.isInstance(node)
}

class ShelvedChangeListNodeConverter : EntityNodeConverter<ShelvedChangeListEntity, ShelvedChangeListNode>(ShelvedChangeListEntity::class) {
  override fun convert(entity: ShelvedChangeListEntity): ShelvedChangeListNode {
    return ShelvedChangeListNode(entity)
  }
}

class RootNodeConverter : EntityNodeConverter<ShelvesTreeRootEntity, ChangesBrowserRootNode>(ShelvesTreeRootEntity::class) {
  override fun convert(entity: ShelvesTreeRootEntity): ChangesBrowserRootNode {
    return ChangesBrowserRootNode()
  }
}

class TagNodeConverter : EntityNodeConverter<TagNodeEntity, TagNode>(TagNodeEntity::class) {
  override fun convert(entity: TagNodeEntity): TagNode {
    return TagNode(entity, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }
}

class ShelvedChangeNodeConverter : EntityNodeConverter<ShelvedChangeEntity, ShelvedChangeNode>(ShelvedChangeEntity::class) {
  override fun convert(entity: ShelvedChangeEntity): ShelvedChangeNode {
    return ShelvedChangeNode(entity)
  }
}