// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child


interface ParentAbEntity : WorkspaceEntity {
  val children: List<@Child ChildAbstractBaseEntity>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ParentAbEntity> {
    override var entitySource: EntitySource
    var children: List<ChildAbstractBaseEntity.Builder<out ChildAbstractBaseEntity>>
  }

  companion object : EntityType<ParentAbEntity, Builder>() {
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
fun MutableEntityStorage.modifyParentAbEntity(
  entity: ParentAbEntity,
  modification: ParentAbEntity.Builder.() -> Unit,
): ParentAbEntity {
  return modifyEntity(ParentAbEntity.Builder::class.java, entity, modification)
}
//endregion

@Abstract
interface ChildAbstractBaseEntity : WorkspaceEntity {
  val commonData: String

  val parentEntity: ParentAbEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder<T : ChildAbstractBaseEntity> : WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    var commonData: String
    var parentEntity: ParentAbEntity.Builder
  }

  companion object : EntityType<ChildAbstractBaseEntity, Builder<ChildAbstractBaseEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      commonData: String,
      entitySource: EntitySource,
      init: (Builder<ChildAbstractBaseEntity>.() -> Unit)? = null,
    ): Builder<ChildAbstractBaseEntity> {
      val builder = builder()
      builder.commonData = commonData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

interface ChildFirstEntity : ChildAbstractBaseEntity {
  val firstData: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildFirstEntity>, ChildAbstractBaseEntity.Builder<ChildFirstEntity> {
    override var entitySource: EntitySource
    override var commonData: String
    override var parentEntity: ParentAbEntity.Builder
    var firstData: String
  }

  companion object : EntityType<ChildFirstEntity, Builder>(ChildAbstractBaseEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      commonData: String,
      firstData: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.commonData = commonData
      builder.firstData = firstData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyChildFirstEntity(
  entity: ChildFirstEntity,
  modification: ChildFirstEntity.Builder.() -> Unit,
): ChildFirstEntity {
  return modifyEntity(ChildFirstEntity.Builder::class.java, entity, modification)
}
//endregion

interface ChildSecondEntity : ChildAbstractBaseEntity {

  // TODO doesn't work at the moment
  //    override val commonData: String

  val secondData: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildSecondEntity>, ChildAbstractBaseEntity.Builder<ChildSecondEntity> {
    override var entitySource: EntitySource
    override var commonData: String
    override var parentEntity: ParentAbEntity.Builder
    var secondData: String
  }

  companion object : EntityType<ChildSecondEntity, Builder>(ChildAbstractBaseEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      commonData: String,
      secondData: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.commonData = commonData
      builder.secondData = secondData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyChildSecondEntity(
  entity: ChildSecondEntity,
  modification: ChildSecondEntity.Builder.() -> Unit,
): ChildSecondEntity {
  return modifyEntity(ChildSecondEntity.Builder::class.java, entity, modification)
}
//endregion
