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
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child



interface ParentSingleAbEntity : WorkspaceEntity {
  val child: @Child ChildSingleAbstractBaseEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ParentSingleAbEntity, WorkspaceEntity.Builder<ParentSingleAbEntity> {
    override var entitySource: EntitySource
    override var child: ChildSingleAbstractBaseEntity?
  }

  companion object : EntityType<ParentSingleAbEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ParentSingleAbEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ParentSingleAbEntity, modification: ParentSingleAbEntity.Builder.() -> Unit) = modifyEntity(
  ParentSingleAbEntity.Builder::class.java, entity, modification)
//endregion

@Abstract
interface ChildSingleAbstractBaseEntity : WorkspaceEntity {
  val commonData: String

  val parentEntity: ParentSingleAbEntity

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder<T : ChildSingleAbstractBaseEntity> : ChildSingleAbstractBaseEntity, WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    override var commonData: String
    override var parentEntity: ParentSingleAbEntity
  }

  companion object : EntityType<ChildSingleAbstractBaseEntity, Builder<ChildSingleAbstractBaseEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(commonData: String,
                        entitySource: EntitySource,
                        init: (Builder<ChildSingleAbstractBaseEntity>.() -> Unit)? = null): ChildSingleAbstractBaseEntity {
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
  @GeneratedCodeApiVersion(2)
  interface Builder : ChildSingleFirstEntity, ChildSingleAbstractBaseEntity.Builder<ChildSingleFirstEntity>, WorkspaceEntity.Builder<ChildSingleFirstEntity> {
    override var entitySource: EntitySource
    override var commonData: String
    override var parentEntity: ParentSingleAbEntity
    override var firstData: String
  }

  companion object : EntityType<ChildSingleFirstEntity, Builder>(ChildSingleAbstractBaseEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(commonData: String,
                        firstData: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ChildSingleFirstEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ChildSingleFirstEntity,
                                      modification: ChildSingleFirstEntity.Builder.() -> Unit) = modifyEntity(
  ChildSingleFirstEntity.Builder::class.java, entity, modification)
//endregion

interface ChildSingleSecondEntity : ChildSingleAbstractBaseEntity {
  val secondData: String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ChildSingleSecondEntity, ChildSingleAbstractBaseEntity.Builder<ChildSingleSecondEntity>, WorkspaceEntity.Builder<ChildSingleSecondEntity> {
    override var entitySource: EntitySource
    override var commonData: String
    override var parentEntity: ParentSingleAbEntity
    override var secondData: String
  }

  companion object : EntityType<ChildSingleSecondEntity, Builder>(ChildSingleAbstractBaseEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(commonData: String,
                        secondData: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ChildSingleSecondEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ChildSingleSecondEntity,
                                      modification: ChildSingleSecondEntity.Builder.() -> Unit) = modifyEntity(
  ChildSingleSecondEntity.Builder::class.java, entity, modification)
//endregion
