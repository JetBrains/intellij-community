// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion

import com.intellij.platform.workspace.storage.WorkspaceEntity
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.MutableEntityStorage



interface ParentAbEntity : WorkspaceEntity {
  val children: List<@Child ChildAbstractBaseEntity>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ParentAbEntity, WorkspaceEntity.Builder<ParentAbEntity> {
    override var entitySource: EntitySource
    override var children: List<ChildAbstractBaseEntity>
  }

  companion object : EntityType<ParentAbEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ParentAbEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ParentAbEntity, modification: ParentAbEntity.Builder.() -> Unit) = modifyEntity(
  ParentAbEntity.Builder::class.java, entity, modification)
//endregion

@Abstract
interface ChildAbstractBaseEntity : WorkspaceEntity {
  val commonData: String

  val parentEntity: ParentAbEntity

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder<T : ChildAbstractBaseEntity> : ChildAbstractBaseEntity, WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    override var commonData: String
    override var parentEntity: ParentAbEntity
  }

  companion object : EntityType<ChildAbstractBaseEntity, Builder<ChildAbstractBaseEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(commonData: String,
                        entitySource: EntitySource,
                        init: (Builder<ChildAbstractBaseEntity>.() -> Unit)? = null): ChildAbstractBaseEntity {
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
  @GeneratedCodeApiVersion(2)
  interface Builder : ChildFirstEntity, ChildAbstractBaseEntity.Builder<ChildFirstEntity>, WorkspaceEntity.Builder<ChildFirstEntity> {
    override var entitySource: EntitySource
    override var commonData: String
    override var parentEntity: ParentAbEntity
    override var firstData: String
  }

  companion object : EntityType<ChildFirstEntity, Builder>(ChildAbstractBaseEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(commonData: String,
                        firstData: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ChildFirstEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ChildFirstEntity, modification: ChildFirstEntity.Builder.() -> Unit) = modifyEntity(
  ChildFirstEntity.Builder::class.java, entity, modification)
//endregion

interface ChildSecondEntity : ChildAbstractBaseEntity {

  // TODO doesn't work at the moment
  //    override val commonData: String

  val secondData: String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ChildSecondEntity, ChildAbstractBaseEntity.Builder<ChildSecondEntity>, WorkspaceEntity.Builder<ChildSecondEntity> {
    override var entitySource: EntitySource
    override var commonData: String
    override var parentEntity: ParentAbEntity
    override var secondData: String
  }

  companion object : EntityType<ChildSecondEntity, Builder>(ChildAbstractBaseEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(commonData: String,
                        secondData: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ChildSecondEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ChildSecondEntity, modification: ChildSecondEntity.Builder.() -> Unit) = modifyEntity(
  ChildSecondEntity.Builder::class.java, entity, modification)
//endregion
