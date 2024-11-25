// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.shared.rhizome

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.project.ProjectEntity
import com.jetbrains.rhizomedb.*
import fleet.kernel.DurableEntityType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.jetbrains.annotations.ApiStatus

/**
 * A provider that supplies a list of entity types related to shelf nodes.
 */
class ShelfNodesEntityTypeProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> = listOf(ShelvesTreeRootEntity,
                                                           ShelvedChangeListEntity,
                                                           ShelvedChangeEntity,
                                                           TagNodeEntity,
                                                           ModuleNodeEntity,
                                                           FilePathNodeEntity,
                                                           RepositoryNodeEntity)
}

/**
 * Represents a common mixin node entity for all shelf tree nodes, which can have children and maintains an order within its parent.
 */
@ApiStatus.Internal
sealed interface NodeEntity : Entity {
  val children: Set<NodeEntity>
    get() = this[Children]

  val orderInParent: Int
    get() = this[Order]

  @ApiStatus.Internal
  companion object : Mixin<NodeEntity>(NodeEntity::class.java.name, "com.intellij") {
    val Children = manyRef<NodeEntity>("children")
    val Order = requiredValue("order", Int.serializer())
  }
}

/**
 * Represents the root entity of a shelves tree structure within a project.
 */
@ApiStatus.Internal
data class ShelvesTreeRootEntity(override val eid: EID) : NodeEntity {
  val project: ProjectEntity by Project

  @ApiStatus.Internal
  companion object : DurableEntityType<ShelvesTreeRootEntity>(ShelvesTreeRootEntity::class.java.name, "com.intellij", ::ShelvesTreeRootEntity, NodeEntity) {
    val Project = requiredRef<ProjectEntity>("project", RefFlags.UNIQUE)
  }
}

@ApiStatus.Internal
@Serializable
data class ShelvedChangeListEntity(override val eid: EID) : NodeEntity {
  val name: String by Name
  val description: String by Description
  val date: Long by Date
  val error: String? by Error
  val isRecycled: Boolean by Recycled
  val isDeleted: Boolean by Deleted

  @ApiStatus.Internal
  companion object : DurableEntityType<ShelvedChangeListEntity>(ShelvedChangeListEntity::class.java.name, "com.intellij", ::ShelvedChangeListEntity, NodeEntity) {
    val Name = requiredValue("name", String.serializer())
    val Description = requiredValue("description", String.serializer())
    val Date = requiredValue("date", Long.serializer())
    val Error = optionalValue("error", String.serializer())
    val Recycled = requiredValue("recycled", Boolean.serializer())
    val Deleted = requiredValue("deleted", Boolean.serializer())
  }
}

@ApiStatus.Internal
@Serializable
data class ShelvedChangeEntity(override val eid: EID) : NodeEntity {
  val filePath: String by FilePath
  val additionalText: String? by AdditionalText
  val fileStatus: String by FileStatus

  @ApiStatus.Internal
  companion object : DurableEntityType<ShelvedChangeEntity>(ShelvedChangeEntity::class.java.name, "com.intellij", ::ShelvedChangeEntity, NodeEntity) {
    val FilePath = requiredValue("filePath", String.serializer())
    val AdditionalText = optionalValue("originText", String.serializer())
    val FileStatus = requiredValue("fileStatus", String.serializer())
  }
}

/**
 * Represents a tag node entity within the changes tree, such as the "Recently deleted" node.
 */
@ApiStatus.Internal
data class TagNodeEntity(override val eid: EID) : NodeEntity {
  val text: String by Text

  @ApiStatus.Internal
  companion object : DurableEntityType<TagNodeEntity>(TagNodeEntity::class.java.name, "com.intellij", ::TagNodeEntity, NodeEntity) {
    val Text = requiredValue("text", String.serializer())
  }
}

@ApiStatus.Internal
data class ModuleNodeEntity(override val eid: EID) : NodeEntity {
  val name: String by Name
  val rootPath: String by RootPath
  val moduleType: String? by ModuleType

  @ApiStatus.Internal
  companion object : DurableEntityType<ModuleNodeEntity>(ModuleNodeEntity::class.java.name, "com.intellij", ::ModuleNodeEntity, NodeEntity) {
    val Name = requiredValue("name", String.serializer())
    val RootPath = requiredValue("rootPath", String.serializer())
    val ModuleType = optionalValue("moduleType", String.serializer())
  }
}

@ApiStatus.Internal
data class RepositoryNodeEntity(override val eid: EID) : NodeEntity {
  val name: String by Name
  val toolTip: String? by ToolTip
  val branchName: String? by BranchName
  val colorRed: Int by ColorRed
  val colorGreen: Int by ColorGreen
  val colorBlue: Int by ColorBlue


  @ApiStatus.Internal
  companion object : DurableEntityType<RepositoryNodeEntity>(RepositoryNodeEntity::class.java.name, "com.intellij", ::RepositoryNodeEntity, NodeEntity) {
    val Name = requiredValue("name", String.serializer())
    val BranchName = optionalValue("branchName", String.serializer())
    val ToolTip = optionalValue("toolTip", String.serializer())
    val ColorRed = requiredValue("colorRed", Int.serializer())
    val ColorGreen = requiredValue("colorGreen", Int.serializer())
    val ColorBlue = requiredValue("colorBlue", Int.serializer())
  }
}

@ApiStatus.Internal
data class FilePathNodeEntity(override val eid: EID) : NodeEntity {
  val name: String by Name
  val parentPath: String? by ParentPath
  val originText: String? by OriginText
  val status: String? by FileStatus
  val isDirectory: Boolean by IsDirectory

  @ApiStatus.Internal
  companion object : DurableEntityType<FilePathNodeEntity>(FilePathNodeEntity::class.java.name, "com.intellij", ::FilePathNodeEntity, NodeEntity) {
    val Name = requiredValue("name", String.serializer())
    val FileStatus = optionalValue("fileStatus", String.serializer())
    val ParentPath = optionalValue("filePath", String.serializer())
    val OriginText = optionalValue("originText", String.serializer())
    val IsDirectory = requiredValue("isDirectory", Boolean.serializer())
  }
}