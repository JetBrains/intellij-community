// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.shared.rhizome

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.project.ProjectEntity
import com.intellij.vcs.impl.shared.changes.PreviewDiffSplitterComponent
import com.jetbrains.rhizomedb.*
import fleet.kernel.DurableEntityType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShelfEntityTypeProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> = listOf(ShelvesTreeRootEntity, ShelvedChangeListEntity, ShelvedChangeEntity, TagNodeEntity, SelectShelveChangeEntity, GroupingItemsEntity, GroupingItemEntity, ModuleNodeEntity, FilePathNodeEntity, RepositoryNodeEntity, DiffSplitterEntity)
}

@ApiStatus.Internal
interface NodeEntity : Entity {
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

@ApiStatus.Internal
data class SelectShelveChangeEntity(override val eid: EID) : Entity {
  val changeList: ShelvedChangeListEntity by ChangeList
  val change: ShelvedChangeEntity? by Change
  val project: ProjectEntity by Project

  @ApiStatus.Internal
  companion object : DurableEntityType<SelectShelveChangeEntity>(SelectShelveChangeEntity::class.java.name, "com.intellij", ::SelectShelveChangeEntity) {
    val ChangeList = requiredRef<ShelvedChangeListEntity>("ChangeList")
    val Change = optionalRef<ShelvedChangeEntity>("Change")
    val Project = requiredRef<ProjectEntity>("project", RefFlags.UNIQUE)
  }
}

@ApiStatus.Internal
data class GroupingItemsEntity(override val eid: EID) : Entity {
  val place: String by Place
  val items by Items

  @ApiStatus.Internal
  companion object : DurableEntityType<GroupingItemsEntity>(GroupingItemsEntity::class.java.name, "com.intellij", ::GroupingItemsEntity) {
    val Place = requiredValue("place", String.serializer())
    val Items = manyRef<GroupingItemEntity>("items", RefFlags.CASCADE_DELETE)
  }
}

@ApiStatus.Internal
data class GroupingItemEntity(override val eid: EID) : Entity {
  val name by Name

  @ApiStatus.Internal
  companion object : DurableEntityType<GroupingItemEntity>(GroupingItemEntity::class.java.name, "com.intellij", ::GroupingItemEntity) {
    val Name = requiredValue("name", String.serializer(), Indexing.UNIQUE)
  }
}

@ApiStatus.Internal
data class DiffSplitterEntity(override val eid: EID) : Entity {
  val splitter: PreviewDiffSplitterComponent by Splitter
  val project: ProjectEntity by Project

  @ApiStatus.Internal
  companion object : DurableEntityType<DiffSplitterEntity>(DiffSplitterEntity::class.java.name, "com.intellij", ::DiffSplitterEntity) {
    val Splitter = requiredTransient<PreviewDiffSplitterComponent>("splitter")
    val Project = requiredRef<ProjectEntity>("project", RefFlags.UNIQUE)
  }
}