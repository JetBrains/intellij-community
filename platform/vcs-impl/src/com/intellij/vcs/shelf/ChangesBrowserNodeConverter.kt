// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.shelf

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserFilePathNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserModuleNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.TagChangesBrowserNode
import com.intellij.platform.vcs.impl.shared.rhizome.FilePathNodeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ModuleNodeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.NodeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.intellij.platform.vcs.impl.shared.rhizome.TagNodeEntity
import com.intellij.vcsUtil.VcsUtil
import fleet.kernel.SharedChangeScope
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass

@ApiStatus.Internal
fun <T : ChangesBrowserNode<*>> SharedChangeScope.convertToEntity(node: T, orderInParent: Int, project: Project): NodeEntity? {
  val converter = NodeToEntityConverter.getConverter(node)
  if (converter == null) {
    NodeToEntityConverter.LOG.error("No converter found for node ${this::class.simpleName}")
    return TagNodeEntity.new {
      it[TagNodeEntity.Text] = node.textPresentation
      it[NodeEntity.Order] = orderInParent
    }
  }
  @Suppress("UNCHECKED_CAST")
  return with(converter as NodeToEntityConverter<T>) {
    convert(node, orderInParent, project)
  }
}

@ApiStatus.Internal
abstract class NodeToEntityConverter<N : ChangesBrowserNode<*>>(private val nodeClass: KClass<N>) {
  abstract fun SharedChangeScope.convert(node: N, orderInParent: Int, project: Project): NodeEntity

  fun isNodeAcceptable(node: ChangesBrowserNode<*>): Boolean = nodeClass.isInstance(node)

  companion object {
    val EP_NAME: ExtensionPointName<NodeToEntityConverter<*>> = ExtensionPointName("com.intellij.vcs.impl.backend.treeNodeConverter")

    fun getConverter(node: ChangesBrowserNode<*>): NodeToEntityConverter<*>? {
      return EP_NAME.extensionList.firstOrNull { it.isNodeAcceptable(node) }
    }

    val LOG: Logger = logger<NodeToEntityConverter<*>>()
  }
}

internal class ShelvedChangeListToEntityConverter : NodeToEntityConverter<ShelvedListNode>(ShelvedListNode::class) {
  override fun SharedChangeScope.convert(node: ShelvedListNode, orderInParent: Int, project: Project): ShelvedChangeListEntity {
    return ShelvedChangeListEntity.new {
      val changeList = node.changeList
      it[ShelvedChangeListEntity.Name] = changeList.name
      it[ShelvedChangeListEntity.Description] = changeList.description
      it[ShelvedChangeListEntity.Date] = changeList.date.toInstant().toEpochMilli()
      it[ShelvedChangeListEntity.Recycled] = changeList.isRecycled
      it[ShelvedChangeListEntity.Deleted] = changeList.isDeleted
      it[NodeEntity.Order] = orderInParent
    }
  }
}

internal class ShelvedChangeNodeConverter : NodeToEntityConverter<ShelvedChangeNode>(ShelvedChangeNode::class) {
  override fun SharedChangeScope.convert(node: ShelvedChangeNode, orderInParent: Int, project: Project): ShelvedChangeEntity {
    return ShelvedChangeEntity.new {
      it[ShelvedChangeEntity.AdditionalText] = node.additionalText
      it[ShelvedChangeEntity.FilePath] = node.shelvedChange.requestName
      it[ShelvedChangeEntity.FileStatus] = node.shelvedChange.fileStatus.id
      it[NodeEntity.Order] = orderInParent
    }

  }
}


internal class TagNodeToEntityConverter : NodeToEntityConverter<TagChangesBrowserNode>(TagChangesBrowserNode::class) {
  override fun SharedChangeScope.convert(node: TagChangesBrowserNode, orderInParent: Int, project: Project): TagNodeEntity {
    return TagNodeEntity.new {
      it[TagNodeEntity.Text] = node.textPresentation
      it[NodeEntity.Order] = orderInParent
    }
  }
}

internal class ModuleNodeToEntityConverter : NodeToEntityConverter<ChangesBrowserModuleNode>(ChangesBrowserModuleNode::class) {
  override fun SharedChangeScope.convert(node: ChangesBrowserModuleNode, orderInParent: Int, project: Project): ModuleNodeEntity {
    return ModuleNodeEntity.new {
            val module = node.userObject
            it[ModuleNodeEntity.Name] = module.name
            it[ModuleNodeEntity.RootPath] = node.moduleRoot.toPresentablePath(project)
            it[ModuleNodeEntity.ModuleType] = module.moduleTypeName
            it[NodeEntity.Order] = orderInParent
    }
  }
}


internal class FilePathNodeToEntityConverter : NodeToEntityConverter<ChangesBrowserFilePathNode>(ChangesBrowserFilePathNode::class) {
  override fun SharedChangeScope.convert(node: ChangesBrowserFilePathNode, orderInParent: Int, project: Project): FilePathNodeEntity {
    return FilePathNodeEntity.new {
            val filePath = node.userObject ?: return@new
            val isFlatten = !ShelfTreeHolder.getInstance(project).isDirectoryGroupingEnabled()
            val name = if (isFlatten) filePath.name else node.getRelativeFilePath(project, filePath)
            it[FilePathNodeEntity.OriginText] = node.getOriginText()
            if (isFlatten) {
              it[FilePathNodeEntity.ParentPath] = filePath.parentPath?.toPresentablePath(project)
            }
            it[FilePathNodeEntity.FileStatus] = node.status?.id
            it[FilePathNodeEntity.IsDirectory] = filePath.isDirectory
            it[FilePathNodeEntity.Name] = name
            it[NodeEntity.Order] = orderInParent
    }
  }
}


private fun FilePath.toPresentablePath(project: Project): String {
  return VcsUtil.getPresentablePath(project, this, true, true)
}