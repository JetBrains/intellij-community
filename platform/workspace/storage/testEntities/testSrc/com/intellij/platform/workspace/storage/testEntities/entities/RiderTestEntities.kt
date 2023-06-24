// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Child


interface ProjectModelTestEntity : WorkspaceEntity {
  val info: String
  val descriptor: Descriptor

  val parentEntity: ProjectModelTestEntity?
  val childrenEntities: List<@Child ProjectModelTestEntity>

  @Child
  val contentRoot: ContentRootTestEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ProjectModelTestEntity, WorkspaceEntity.Builder<ProjectModelTestEntity> {
    override var entitySource: EntitySource
    override var info: String
    override var descriptor: Descriptor
    override var parentEntity: ProjectModelTestEntity?
    override var childrenEntities: List<ProjectModelTestEntity>
    override var contentRoot: ContentRootTestEntity?
  }

  companion object : EntityType<ProjectModelTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(info: String,
                        descriptor: Descriptor,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ProjectModelTestEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ProjectModelTestEntity,
                                      modification: ProjectModelTestEntity.Builder.() -> Unit) = modifyEntity(
  ProjectModelTestEntity.Builder::class.java, entity, modification)
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