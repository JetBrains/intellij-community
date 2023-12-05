// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child

interface ParentWithExtensionEntity : WorkspaceEntity {
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ParentWithExtensionEntity, WorkspaceEntity.Builder<ParentWithExtensionEntity> {
    override var entitySource: EntitySource
    override var data: String
  }

  companion object : EntityType<ParentWithExtensionEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ParentWithExtensionEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ParentWithExtensionEntity,
                                      modification: ParentWithExtensionEntity.Builder.() -> Unit): ParentWithExtensionEntity = modifyEntity(
  ParentWithExtensionEntity.Builder::class.java, entity, modification)

var ParentWithExtensionEntity.Builder.child: @Child AbstractChildEntity?
  by WorkspaceEntity.extension()
//endregion

@Abstract
interface AbstractChildEntity : WorkspaceEntity {
  val data: String
  val parent: ParentWithExtensionEntity

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder<T : AbstractChildEntity> : AbstractChildEntity, WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    override var data: String
    override var parent: ParentWithExtensionEntity
  }

  companion object : EntityType<AbstractChildEntity, Builder<AbstractChildEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String,
                        entitySource: EntitySource,
                        init: (Builder<AbstractChildEntity>.() -> Unit)? = null): AbstractChildEntity {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

interface SpecificChildEntity : AbstractChildEntity {
  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : SpecificChildEntity, AbstractChildEntity.Builder<SpecificChildEntity>, WorkspaceEntity.Builder<SpecificChildEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var parent: ParentWithExtensionEntity
  }

  companion object : EntityType<SpecificChildEntity, Builder>(AbstractChildEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SpecificChildEntity {
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
fun MutableEntityStorage.modifyEntity(entity: SpecificChildEntity,
                                      modification: SpecificChildEntity.Builder.() -> Unit): SpecificChildEntity = modifyEntity(
  SpecificChildEntity.Builder::class.java, entity, modification)
//endregion

val ParentWithExtensionEntity.child: @Child AbstractChildEntity? by WorkspaceEntity.extension()