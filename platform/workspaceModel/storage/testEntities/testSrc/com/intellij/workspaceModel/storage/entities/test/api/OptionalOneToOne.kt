// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

interface OptionalOneToOneParentEntity : WorkspaceEntity {
  val child: @Child OptionalOneToOneChildEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : OptionalOneToOneParentEntity, WorkspaceEntity.Builder<OptionalOneToOneParentEntity>, ObjBuilder<OptionalOneToOneParentEntity> {
    override var entitySource: EntitySource
    override var child: OptionalOneToOneChildEntity?
  }

  companion object : Type<OptionalOneToOneParentEntity, Builder>() {
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OptionalOneToOneParentEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: OptionalOneToOneParentEntity,
                                      modification: OptionalOneToOneParentEntity.Builder.() -> Unit) = modifyEntity(
  OptionalOneToOneParentEntity.Builder::class.java, entity, modification)
//endregion

interface OptionalOneToOneChildEntity : WorkspaceEntity {
  val data: String
  val parent: OptionalOneToOneParentEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : OptionalOneToOneChildEntity, WorkspaceEntity.Builder<OptionalOneToOneChildEntity>, ObjBuilder<OptionalOneToOneChildEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var parent: OptionalOneToOneParentEntity?
  }

  companion object : Type<OptionalOneToOneChildEntity, Builder>() {
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OptionalOneToOneChildEntity {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: OptionalOneToOneChildEntity,
                                      modification: OptionalOneToOneChildEntity.Builder.() -> Unit) = modifyEntity(
  OptionalOneToOneChildEntity.Builder::class.java, entity, modification)
//endregion
