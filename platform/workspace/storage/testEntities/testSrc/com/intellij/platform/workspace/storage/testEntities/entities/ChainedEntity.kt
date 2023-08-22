// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Child


interface ChainedParentEntity : WorkspaceEntity {
  val child: List<@Child ChainedEntity>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ChainedParentEntity, WorkspaceEntity.Builder<ChainedParentEntity> {
    override var entitySource: EntitySource
    override var child: List<ChainedEntity>
  }

  companion object : EntityType<ChainedParentEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChainedParentEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ChainedParentEntity, modification: ChainedParentEntity.Builder.() -> Unit) = modifyEntity(
  ChainedParentEntity.Builder::class.java, entity, modification)
//endregion

interface ChainedEntity : WorkspaceEntity {
  val data: String
  val parent: ChainedEntity?
  val child: @Child ChainedEntity?
  val generalParent: ChainedParentEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ChainedEntity, WorkspaceEntity.Builder<ChainedEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var parent: ChainedEntity?
    override var child: ChainedEntity?
    override var generalParent: ChainedParentEntity?
  }

  companion object : EntityType<ChainedEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChainedEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ChainedEntity, modification: ChainedEntity.Builder.() -> Unit) = modifyEntity(
  ChainedEntity.Builder::class.java, entity, modification)
//endregion
