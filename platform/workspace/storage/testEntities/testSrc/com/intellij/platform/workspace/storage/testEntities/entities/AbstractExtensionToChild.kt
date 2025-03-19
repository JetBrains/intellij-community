// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child

interface ParentWithExtensionEntity : WorkspaceEntity {
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ParentWithExtensionEntity> {
    override var entitySource: EntitySource
    var data: String
  }

  companion object : EntityType<ParentWithExtensionEntity, Builder>() {
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
fun MutableEntityStorage.modifyParentWithExtensionEntity(
  entity: ParentWithExtensionEntity,
  modification: ParentWithExtensionEntity.Builder.() -> Unit,
): ParentWithExtensionEntity {
  return modifyEntity(ParentWithExtensionEntity.Builder::class.java, entity, modification)
}

var ParentWithExtensionEntity.Builder.child: @Child AbstractChildEntity.Builder<out AbstractChildEntity>?
  by WorkspaceEntity.extensionBuilder(AbstractChildEntity::class.java)
//endregion

@Abstract
interface AbstractChildEntity : WorkspaceEntity {
  val data: String
  val parent: ParentWithExtensionEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder<T : AbstractChildEntity> : WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    var data: String
    var parent: ParentWithExtensionEntity.Builder
  }

  companion object : EntityType<AbstractChildEntity, Builder<AbstractChildEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder<AbstractChildEntity>.() -> Unit)? = null,
    ): Builder<AbstractChildEntity> {
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
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SpecificChildEntity>, AbstractChildEntity.Builder<SpecificChildEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var parent: ParentWithExtensionEntity.Builder
  }

  companion object : EntityType<SpecificChildEntity, Builder>(AbstractChildEntity) {
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
fun MutableEntityStorage.modifySpecificChildEntity(
  entity: SpecificChildEntity,
  modification: SpecificChildEntity.Builder.() -> Unit,
): SpecificChildEntity {
  return modifyEntity(SpecificChildEntity.Builder::class.java, entity, modification)
}
//endregion

val ParentWithExtensionEntity.child: @Child AbstractChildEntity? by WorkspaceEntity.extension()