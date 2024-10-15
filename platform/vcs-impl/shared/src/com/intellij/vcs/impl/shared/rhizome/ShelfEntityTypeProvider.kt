// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.shared.rhizome

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.project.ProjectEntity
import com.jetbrains.rhizomedb.*
import fleet.kernel.DurableEntityType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

class ShelfEntityTypeProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> = listOf(ShelvesTreeRootEntity, ShelvedChangeListEntity, ShelvedChangeEntity, TagNodeEntity, SelectShelveChangeEntity)
}

interface NodeEntity : Entity {
  val children: Set<NodeEntity>
    get() = this[Children]

  val orderInParent: Int
    get() = this[Order]

  companion object : Mixin<NodeEntity>(NodeEntity::class.java.name, "com.intellij") {
    val Children = manyRef<NodeEntity>("children")
    val Order = requiredValue("order", Int.serializer())
  }
}

class ShelvesTreeRootEntity(override val eid: EID) : NodeEntity {
  val project: ProjectEntity by Project

  companion object : DurableEntityType<ShelvesTreeRootEntity>(ShelvesTreeRootEntity::class.java.name, "com.intellij", ::ShelvesTreeRootEntity, NodeEntity) {
    val Project = requiredRef<ProjectEntity>("project")
  }
}

@Serializable
class ShelvedChangeListEntity(override val eid: EID) : NodeEntity {
  val name: String by Name
  val description: String by Description
  val date: Long by Date
  val error: String? by Error
  val isRecycled: Boolean by Recycled
  val isDeleted: Boolean by Deleted

  val isMarkedToDelete: Boolean by MarkedToDelete

  companion object : DurableEntityType<ShelvedChangeListEntity>(ShelvedChangeListEntity::class.java.name, "com.intellij", ::ShelvedChangeListEntity, NodeEntity) {
    val Name = requiredValue("name", String.serializer())
    val Description = requiredValue("description", String.serializer())
    val Date = requiredValue("date", Long.serializer())
    val Error = optionalValue("error", String.serializer())
    val Recycled = requiredValue("recycled", Boolean.serializer())
    val Deleted = requiredValue("deleted", Boolean.serializer())
    val MarkedToDelete = requiredValue("markedToDelete", Boolean.serializer())
  }
}

@Serializable
class ShelvedChangeEntity(override val eid: EID) : NodeEntity {
  val filePath: String by FilePath
  val additionalText: String? by AdditionalText
  val fileStatus: String by FileStatus

  companion object : DurableEntityType<ShelvedChangeEntity>(ShelvedChangeEntity::class.java.name, "com.intellij", ::ShelvedChangeEntity, NodeEntity) {
    val FilePath = requiredValue("filePath", String.serializer())
    val AdditionalText = optionalValue("originText", String.serializer())
    val FileStatus = requiredValue("fileStatus", String.serializer())
  }
}

class TagNodeEntity(override val eid: EID) : NodeEntity {
  val text: String by Text

  companion object : DurableEntityType<TagNodeEntity>(TagNodeEntity::class.java.name, "com.intellij", ::TagNodeEntity, NodeEntity) {
    val Text = requiredValue("text", String.serializer())
  }
}

class SelectShelveChangeEntity(override val eid: EID) : Entity {
  val changeList: ShelvedChangeListEntity by ChangeList
  val change: ShelvedChangeEntity by Change
  val project: ProjectEntity by Project

  companion object : DurableEntityType<SelectShelveChangeEntity>(SelectShelveChangeEntity::class.java.name, "com.intellij", ::SelectShelveChangeEntity) {
    val ChangeList = requiredRef<ShelvedChangeListEntity>("ChangeList")
    val Change = requiredRef<ShelvedChangeEntity>("Change")
    val Project = requiredRef<ProjectEntity>("project")
  }
}