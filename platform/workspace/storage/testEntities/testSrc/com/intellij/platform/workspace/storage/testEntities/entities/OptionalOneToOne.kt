// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Child

interface OptionalOneToOneParentEntity : WorkspaceEntity {
  val child: @Child OptionalOneToOneChildEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : OptionalOneToOneParentEntity, WorkspaceEntity.Builder<OptionalOneToOneParentEntity> {
    override var entitySource: EntitySource
    override var child: OptionalOneToOneChildEntity?
  }

  companion object : EntityType<OptionalOneToOneParentEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
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
  @GeneratedCodeApiVersion(2)
  interface Builder : OptionalOneToOneChildEntity, WorkspaceEntity.Builder<OptionalOneToOneChildEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var parent: OptionalOneToOneParentEntity?
  }

  companion object : EntityType<OptionalOneToOneChildEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
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
