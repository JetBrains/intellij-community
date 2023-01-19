// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child


interface ProjectModelTestEntity : WorkspaceEntity {
  val info: String
  val descriptor: Descriptor

  val parentEntity: ProjectModelTestEntity?
  val childrenEntities: List<@Child ProjectModelTestEntity>

  @Child
  val contentRoot: ContentRootEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ProjectModelTestEntity, WorkspaceEntity.Builder<ProjectModelTestEntity>, ObjBuilder<ProjectModelTestEntity> {
    override var entitySource: EntitySource
    override var info: String
    override var descriptor: Descriptor
    override var parentEntity: ProjectModelTestEntity?
    override var childrenEntities: List<ProjectModelTestEntity>
    override var contentRoot: ContentRootEntity?
  }

  companion object : Type<ProjectModelTestEntity, Builder>() {
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

var ContentRootEntity.Builder.projectModelTestEntity: ProjectModelTestEntity?
  by WorkspaceEntity.extension()
//endregion

private val ContentRootEntity.projectModelTestEntity: ProjectModelTestEntity? by WorkspaceEntity.extension()


open class Descriptor(val data: String)
class DescriptorInstance(data: String) : Descriptor(data)