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



interface ParentChainEntity : WorkspaceEntity {
  val root: @Child CompositeAbstractEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ParentChainEntity, WorkspaceEntity.Builder<ParentChainEntity> {
    override var entitySource: EntitySource
    override var root: CompositeAbstractEntity?
  }

  companion object : EntityType<ParentChainEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ParentChainEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ParentChainEntity, modification: ParentChainEntity.Builder.() -> Unit) = modifyEntity(
  ParentChainEntity.Builder::class.java, entity, modification)
//endregion

@Abstract
interface SimpleAbstractEntity : WorkspaceEntity {

  val parentInList: CompositeAbstractEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder<T : SimpleAbstractEntity> : SimpleAbstractEntity, WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    override var parentInList: CompositeAbstractEntity?
  }

  companion object : EntityType<SimpleAbstractEntity, Builder<SimpleAbstractEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder<SimpleAbstractEntity>.() -> Unit)? = null): SimpleAbstractEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

@Abstract
interface CompositeAbstractEntity : SimpleAbstractEntity {
  val children: List<@Child SimpleAbstractEntity>

  val parentEntity: ParentChainEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder<T : CompositeAbstractEntity> : CompositeAbstractEntity, SimpleAbstractEntity.Builder<T>, WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    override var parentInList: CompositeAbstractEntity?
    override var children: List<SimpleAbstractEntity>
    override var parentEntity: ParentChainEntity?
  }

  companion object : EntityType<CompositeAbstractEntity, Builder<CompositeAbstractEntity>>(SimpleAbstractEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder<CompositeAbstractEntity>.() -> Unit)? = null): CompositeAbstractEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

interface CompositeChildAbstractEntity : CompositeAbstractEntity {
  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : CompositeChildAbstractEntity, CompositeAbstractEntity.Builder<CompositeChildAbstractEntity>, WorkspaceEntity.Builder<CompositeChildAbstractEntity> {
    override var entitySource: EntitySource
    override var parentInList: CompositeAbstractEntity?
    override var children: List<SimpleAbstractEntity>
    override var parentEntity: ParentChainEntity?
  }

  companion object : EntityType<CompositeChildAbstractEntity, Builder>(CompositeAbstractEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): CompositeChildAbstractEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: CompositeChildAbstractEntity,
                                      modification: CompositeChildAbstractEntity.Builder.() -> Unit) = modifyEntity(
  CompositeChildAbstractEntity.Builder::class.java, entity, modification)
//endregion

interface SimpleChildAbstractEntity : SimpleAbstractEntity {
  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : SimpleChildAbstractEntity, SimpleAbstractEntity.Builder<SimpleChildAbstractEntity>, WorkspaceEntity.Builder<SimpleChildAbstractEntity> {
    override var entitySource: EntitySource
    override var parentInList: CompositeAbstractEntity?
  }

  companion object : EntityType<SimpleChildAbstractEntity, Builder>(SimpleAbstractEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SimpleChildAbstractEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: SimpleChildAbstractEntity,
                                      modification: SimpleChildAbstractEntity.Builder.() -> Unit) = modifyEntity(
  SimpleChildAbstractEntity.Builder::class.java, entity, modification)
//endregion
