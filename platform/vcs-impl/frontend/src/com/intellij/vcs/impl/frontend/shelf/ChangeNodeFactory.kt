// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.vcs.impl.frontend.shelf

import com.intellij.ui.SimpleTextAttributes
import com.intellij.vcs.impl.frontend.changes.tree.FilePathTreeNode
import com.intellij.vcs.impl.frontend.changes.tree.ModuleTreeNode
import com.intellij.vcs.impl.frontend.changes.tree.RepositoryTreeNode
import com.intellij.vcs.impl.frontend.shelf.tree.*
import com.intellij.vcs.impl.shared.rhizome.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.reflect.KClass

private val converters = listOf(ShelvedChangeListNodeConverter(), RootNodeConverter(), TagNodeConverter(), ShelvedChangeNodeConverter(), ModuleNodeConverter(), FileNodeConverter(), RepositoryNodeConverter())


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

@ApiStatus.Internal
abstract class EntityNodeConverter<E : NodeEntity, N : ChangesBrowserNode<*>>(val acceptedClass: KClass<E>) {
  abstract fun convert(entity: E): N

  fun isNodeAcceptable(node: NodeEntity): Boolean = acceptedClass.isInstance(node)
}

@ApiStatus.Internal
class ShelvedChangeListNodeConverter : EntityNodeConverter<ShelvedChangeListEntity, ShelvedChangeListNode>(ShelvedChangeListEntity::class) {
  override fun convert(entity: ShelvedChangeListEntity): ShelvedChangeListNode {
    return ShelvedChangeListNode(entity)
  }
}

@ApiStatus.Internal
class RootNodeConverter : EntityNodeConverter<ShelvesTreeRootEntity, ChangesBrowserRootNode>(ShelvesTreeRootEntity::class) {
  override fun convert(entity: ShelvesTreeRootEntity): ChangesBrowserRootNode {
    return ChangesBrowserRootNode()
  }
}

@ApiStatus.Internal
class TagNodeConverter : EntityNodeConverter<TagNodeEntity, TagNode>(TagNodeEntity::class) {
  override fun convert(entity: TagNodeEntity): TagNode {
    return TagNode(entity, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }
}

@ApiStatus.Internal
class ShelvedChangeNodeConverter : EntityNodeConverter<ShelvedChangeEntity, ShelvedChangeNode>(ShelvedChangeEntity::class) {
  override fun convert(entity: ShelvedChangeEntity): ShelvedChangeNode {
    return ShelvedChangeNode(entity)
  }
}

@ApiStatus.Internal
class ModuleNodeConverter : EntityNodeConverter<ModuleNodeEntity, ModuleTreeNode>(ModuleNodeEntity::class) {
  override fun convert(entity: ModuleNodeEntity): ModuleTreeNode {
    return ModuleTreeNode(entity)
  }
}

@ApiStatus.Internal
class FileNodeConverter : EntityNodeConverter<FilePathNodeEntity, FilePathTreeNode>(FilePathNodeEntity::class) {
  override fun convert(entity: FilePathNodeEntity): FilePathTreeNode {
    return FilePathTreeNode(entity)
  }
}

@ApiStatus.Internal
class RepositoryNodeConverter : EntityNodeConverter<RepositoryNodeEntity, ChangesBrowserNode<*>>(RepositoryNodeEntity::class) {
  override fun convert(entity: RepositoryNodeEntity): ChangesBrowserNode<*> {
    return RepositoryTreeNode(entity)
  }
}