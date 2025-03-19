// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child


interface ParentSingleAbEntity : WorkspaceEntity {
  val child: @Child ChildSingleAbstractBaseEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ParentSingleAbEntity> {
    override var entitySource: EntitySource
    var child: ChildSingleAbstractBaseEntity.Builder<out ChildSingleAbstractBaseEntity>?
  }

  companion object : EntityType<ParentSingleAbEntity, Builder>() {
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
fun MutableEntityStorage.modifyParentSingleAbEntity(
  entity: ParentSingleAbEntity,
  modification: ParentSingleAbEntity.Builder.() -> Unit,
): ParentSingleAbEntity {
  return modifyEntity(ParentSingleAbEntity.Builder::class.java, entity, modification)
}
//endregion

@Abstract
interface ChildSingleAbstractBaseEntity : WorkspaceEntity {
  val commonData: String

  val parentEntity: ParentSingleAbEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder<T : ChildSingleAbstractBaseEntity> : WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    var commonData: String
    var parentEntity: ParentSingleAbEntity.Builder
  }

  companion object : EntityType<ChildSingleAbstractBaseEntity, Builder<ChildSingleAbstractBaseEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      commonData: String,
      entitySource: EntitySource,
      init: (Builder<ChildSingleAbstractBaseEntity>.() -> Unit)? = null,
    ): Builder<ChildSingleAbstractBaseEntity> {
      val builder = builder()
      builder.commonData = commonData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

interface ChildSingleFirstEntity : ChildSingleAbstractBaseEntity {
  val firstData: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildSingleFirstEntity>, ChildSingleAbstractBaseEntity.Builder<ChildSingleFirstEntity> {
    override var entitySource: EntitySource
    override var commonData: String
    override var parentEntity: ParentSingleAbEntity.Builder
    var firstData: String
  }

  companion object : EntityType<ChildSingleFirstEntity, Builder>(ChildSingleAbstractBaseEntity) {
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
fun MutableEntityStorage.modifyChildSingleFirstEntity(
  entity: ChildSingleFirstEntity,
  modification: ChildSingleFirstEntity.Builder.() -> Unit,
): ChildSingleFirstEntity {
  return modifyEntity(ChildSingleFirstEntity.Builder::class.java, entity, modification)
}
//endregion

interface ChildSingleSecondEntity : ChildSingleAbstractBaseEntity {
  val secondData: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildSingleSecondEntity>, ChildSingleAbstractBaseEntity.Builder<ChildSingleSecondEntity> {
    override var entitySource: EntitySource
    override var commonData: String
    override var parentEntity: ParentSingleAbEntity.Builder
    var secondData: String
  }

  companion object : EntityType<ChildSingleSecondEntity, Builder>(ChildSingleAbstractBaseEntity) {
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
fun MutableEntityStorage.modifyChildSingleSecondEntity(
  entity: ChildSingleSecondEntity,
  modification: ChildSingleSecondEntity.Builder.() -> Unit,
): ChildSingleSecondEntity {
  return modifyEntity(ChildSingleSecondEntity.Builder::class.java, entity, modification)
}
//endregion
