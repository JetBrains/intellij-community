// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserFilePathNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserModuleNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.TagChangesBrowserNode
import com.intellij.platform.kernel.withKernel
import com.intellij.vcs.impl.shared.rhizome.*
import com.intellij.vcsUtil.VcsUtil
import fleet.kernel.change
import fleet.kernel.shared
import kotlin.reflect.KClass

suspend fun <T : ChangesBrowserNode<*>> T.convertToEntity(tree: ShelfTree, orderInParent: Int, project: Project): NodeEntity? {
  val converter = NodeToEntityConverter.getConverter(this)
  if (converter == null) {
    println("converter for $this not found")
    return null
  }
  @Suppress("UNCHECKED_CAST")
  return (converter as NodeToEntityConverter<T, NodeEntity>).convert(this@convertToEntity, tree, orderInParent, project)
}


abstract class NodeToEntityConverter<N : ChangesBrowserNode<*>, E : NodeEntity>(private val nodeClass: KClass<N>) {
  abstract suspend fun convert(node: N, tree: ShelfTree, orderInParent: Int, project: Project): E

  fun isNodeAcceptable(node: ChangesBrowserNode<*>): Boolean = nodeClass.isInstance(node)

  companion object {
    val EP_NAME: ExtensionPointName<NodeToEntityConverter<*, *>> = ExtensionPointName("com.intellij.vcs.impl.backend.treeNodeConverter")

    fun getConverter(node: ChangesBrowserNode<*>): NodeToEntityConverter<*, *>? {
      return EP_NAME.extensionList.firstOrNull { it.isNodeAcceptable(node) }
    }
  }
}

internal class ShelvedChangeListToEntityConverter : NodeToEntityConverter<ShelvedListNode, ShelvedChangeListEntity>(ShelvedListNode::class) {
  override suspend fun convert(node: ShelvedListNode, tree: ShelfTree, orderInParent: Int, project: Project): ShelvedChangeListEntity {
    return withKernel {
      change {
        shared {
          ShelvedChangeListEntity.new {
            val changeList = node.changeList
            it[ShelvedChangeListEntity.Name] = changeList.name
            it[ShelvedChangeListEntity.Description] = changeList.description
            it[ShelvedChangeListEntity.Date] = changeList.date.toInstant().toEpochMilli()
            it[ShelvedChangeListEntity.Recycled] = changeList.isRecycled
            it[ShelvedChangeListEntity.Deleted] = changeList.isDeleted
            it[ShelvedChangeListEntity.MarkedToDelete] = changeList.isMarkedToDelete
            it[NodeEntity.Order] = orderInParent
          }
        }
      }
    }
  }
}

internal class ShelvedChangeNodeConverter : NodeToEntityConverter<ShelvedChangeNode, ShelvedChangeEntity>(ShelvedChangeNode::class) {
  override suspend fun convert(node: ShelvedChangeNode, tree: ShelfTree, orderInParent: Int, project: Project): ShelvedChangeEntity {
    return withKernel {
      change {
        shared {
          ShelvedChangeEntity.new {
            it[ShelvedChangeEntity.AdditionalText] = node.additionalText
            it[ShelvedChangeEntity.FilePath] = node.shelvedChange.requestName
            it[ShelvedChangeEntity.FileStatus] = node.shelvedChange.fileStatus.id
            it[NodeEntity.Order] = orderInParent
          }
        }
      }
    }
  }
}


internal class TagNodeToEntityConverter : NodeToEntityConverter<TagChangesBrowserNode, TagNodeEntity>(TagChangesBrowserNode::class) {
  override suspend fun convert(node: TagChangesBrowserNode, tree: ShelfTree, orderInParent: Int, project: Project): TagNodeEntity {
    return withKernel {
      change {
        shared {
          TagNodeEntity.new {
            it[TagNodeEntity.Text] = node.textPresentation
            it[NodeEntity.Order] = orderInParent
          }
        }
      }
    }
  }
}
