// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf

import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.TagChangesBrowserNode
import com.intellij.platform.kernel.withKernel
import com.intellij.vcs.impl.shared.rhizome.NodeEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.intellij.vcs.impl.shared.rhizome.TagNodeEntity
import fleet.kernel.change
import fleet.kernel.shared
import kotlin.reflect.KClass

private val converters: List<NodeToEntityConverter<*, *>> = listOf(ShelvedChangeListToEntityConverter, TagNodeToEntityConverter, ShelvedChangeNodeConverter)

fun <T : ChangesBrowserNode<*>> T.convertToEntity(orderInParent: Int): NodeEntity? {
  val converter = converters.firstOrNull { it.isNodeAcceptable(this) }
  if (converter == null) {
    println("converter for $this not found")
    return null
  }
  @Suppress("UNCHECKED_CAST")
  return runBlockingCancellable { (converter as NodeToEntityConverter<T, NodeEntity>).convert(this@convertToEntity, orderInParent) }
}


abstract class NodeToEntityConverter<N : ChangesBrowserNode<*>, E : NodeEntity>(private val nodeClass: KClass<N>) {
  abstract suspend fun convert(node: N, orderInParent: Int): E

  fun isNodeAcceptable(node: ChangesBrowserNode<*>): Boolean = nodeClass.isInstance(node)
}

private object ShelvedChangeListToEntityConverter : NodeToEntityConverter<ShelvedListNode, ShelvedChangeListEntity>(ShelvedListNode::class) {
  override suspend fun convert(node: ShelvedListNode, orderInParent: Int): ShelvedChangeListEntity {
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

private object ShelvedChangeNodeConverter : NodeToEntityConverter<ShelvedChangeNode, ShelvedChangeEntity>(ShelvedChangeNode::class) {
  override suspend fun convert(node: ShelvedChangeNode, orderInParent: Int): ShelvedChangeEntity {
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


private object TagNodeToEntityConverter : NodeToEntityConverter<TagChangesBrowserNode, TagNodeEntity>(TagChangesBrowserNode::class) {
  override suspend fun convert(node: TagChangesBrowserNode, orderInParent: Int): TagNodeEntity {
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
