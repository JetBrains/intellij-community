// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child


interface ProjectModelTestEntity : WorkspaceEntity {
  val info: String
  val descriptor: Descriptor

  val parentEntity: ProjectModelTestEntity?
  val childrenEntities: List<@Child ProjectModelTestEntity>

  @Child
  val contentRoot: ContentRootTestEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ProjectModelTestEntity> {
    override var entitySource: EntitySource
    var info: String
    var descriptor: Descriptor
    var parentEntity: ProjectModelTestEntity.Builder?
    var childrenEntities: List<ProjectModelTestEntity.Builder>
    var contentRoot: ContentRootTestEntity.Builder?
  }

  companion object : EntityType<ProjectModelTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      info: String,
      descriptor: Descriptor,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.info = info
      builder.descriptor = descriptor
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyProjectModelTestEntity(
  entity: ProjectModelTestEntity,
  modification: ProjectModelTestEntity.Builder.() -> Unit,
): ProjectModelTestEntity {
  return modifyEntity(ProjectModelTestEntity.Builder::class.java, entity, modification)
}
//endregion

private val ContentRootTestEntity.projectModelTestEntity: ProjectModelTestEntity? by WorkspaceEntity.extension()


open class Descriptor(val data: String) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Descriptor

    return data == other.data
  }

  override fun hashCode(): Int {
    return data.hashCode()
  }
}
class DescriptorInstance(data: String) : Descriptor(data)