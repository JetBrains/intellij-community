// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type


interface ProjectModelTestEntity : WorkspaceEntity {
  val info: String
  val descriptor: Descriptor

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ProjectModelTestEntity, WorkspaceEntity.Builder<ProjectModelTestEntity>, ObjBuilder<ProjectModelTestEntity> {
    override var entitySource: EntitySource
    override var info: String
    override var descriptor: Descriptor
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
//endregion


open class Descriptor(val data: String)
class DescriptorInstance(data: String) : Descriptor(data)