// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.platform.vcs.impl.frontend.shelf

import com.intellij.platform.vcs.impl.frontend.shelf.tree.ChangesBrowserNode
import com.intellij.ui.SimpleTextAttributes
import com.intellij.platform.vcs.impl.frontend.changes.tree.FilePathTreeNode
import com.intellij.platform.vcs.impl.frontend.changes.tree.ModuleTreeNode
import com.intellij.platform.vcs.impl.frontend.changes.tree.RepositoryTreeNode
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ChangeSelectionIdentifier
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ChangelistSelectionIdentifier
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ChangesBrowserRootNode
import com.intellij.platform.vcs.impl.frontend.shelf.tree.SELECTION_IDENTIFIER_KEY
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ShelvedChangeListNode
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ShelvedChangeNode
import com.intellij.platform.vcs.impl.frontend.shelf.tree.TagNode
import com.intellij.platform.vcs.impl.shared.rhizome.FilePathNodeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ModuleNodeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.NodeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.RepositoryNodeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvesTreeRootEntity
import com.intellij.platform.vcs.impl.shared.rhizome.TagNodeEntity
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.reflect.KClass

private val converters = listOf(ShelvedChangeListNodeConverter, RootNodeConverter, TagNodeConverter, ShelvedChangeNodeConverter, ModuleNodeConverter, FileNodeConverter, RepositoryNodeConverter)

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
object ShelvedChangeListNodeConverter : EntityNodeConverter<ShelvedChangeListEntity, ShelvedChangeListNode>(ShelvedChangeListEntity::class) {
  override fun convert(entity: ShelvedChangeListEntity): ShelvedChangeListNode {
    val shelvedChangeListNode = ShelvedChangeListNode(entity)
    shelvedChangeListNode.putUserData(SELECTION_IDENTIFIER_KEY, ChangelistSelectionIdentifier(entity))
    return shelvedChangeListNode
  }
}

@ApiStatus.Internal
object RootNodeConverter : EntityNodeConverter<ShelvesTreeRootEntity, ChangesBrowserRootNode>(ShelvesTreeRootEntity::class) {
  override fun convert(entity: ShelvesTreeRootEntity): ChangesBrowserRootNode {
    return ChangesBrowserRootNode()
  }
}

@ApiStatus.Internal
object TagNodeConverter : EntityNodeConverter<TagNodeEntity, TagNode>(TagNodeEntity::class) {
  override fun convert(entity: TagNodeEntity): TagNode {
    return TagNode(entity, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }
}

@ApiStatus.Internal
object ShelvedChangeNodeConverter : EntityNodeConverter<ShelvedChangeEntity, ShelvedChangeNode>(ShelvedChangeEntity::class) {
  override fun convert(entity: ShelvedChangeEntity): ShelvedChangeNode {
    val shelvedChangeNode = ShelvedChangeNode(entity)
    shelvedChangeNode.putUserData(SELECTION_IDENTIFIER_KEY, ChangeSelectionIdentifier(entity))
    return shelvedChangeNode
  }
}

@ApiStatus.Internal
object ModuleNodeConverter : EntityNodeConverter<ModuleNodeEntity, ModuleTreeNode>(ModuleNodeEntity::class) {
  override fun convert(entity: ModuleNodeEntity): ModuleTreeNode {
    return ModuleTreeNode(entity)
  }
}

@ApiStatus.Internal
object FileNodeConverter : EntityNodeConverter<FilePathNodeEntity, FilePathTreeNode>(FilePathNodeEntity::class) {
  override fun convert(entity: FilePathNodeEntity): FilePathTreeNode {
    return FilePathTreeNode(entity)
  }
}

@ApiStatus.Internal
object RepositoryNodeConverter : EntityNodeConverter<RepositoryNodeEntity, ChangesBrowserNode<*>>(RepositoryNodeEntity::class) {
  override fun convert(entity: RepositoryNodeEntity): ChangesBrowserNode<*> {
    return RepositoryTreeNode(entity)
  }
}