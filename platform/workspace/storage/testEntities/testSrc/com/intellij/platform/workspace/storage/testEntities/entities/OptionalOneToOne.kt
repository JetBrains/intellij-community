// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child

interface OptionalOneToOneParentEntity : WorkspaceEntity {
  val child: @Child OptionalOneToOneChildEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<OptionalOneToOneParentEntity> {
    override var entitySource: EntitySource
    var child: OptionalOneToOneChildEntity.Builder?
  }

  companion object : EntityType<OptionalOneToOneParentEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyOptionalOneToOneParentEntity(
  entity: OptionalOneToOneParentEntity,
  modification: OptionalOneToOneParentEntity.Builder.() -> Unit,
): OptionalOneToOneParentEntity {
  return modifyEntity(OptionalOneToOneParentEntity.Builder::class.java, entity, modification)
}
//endregion

interface OptionalOneToOneChildEntity : WorkspaceEntity {
  val data: String
  val parent: OptionalOneToOneParentEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<OptionalOneToOneChildEntity> {
    override var entitySource: EntitySource
    var data: String
    var parent: OptionalOneToOneParentEntity.Builder?
  }

  companion object : EntityType<OptionalOneToOneChildEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyOptionalOneToOneChildEntity(
  entity: OptionalOneToOneChildEntity,
  modification: OptionalOneToOneChildEntity.Builder.() -> Unit,
): OptionalOneToOneChildEntity {
  return modifyEntity(OptionalOneToOneChildEntity.Builder::class.java, entity, modification)
}
//endregion
