// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child


interface ParentChainEntity : WorkspaceEntity {
  val root: @Child CompositeAbstractEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ParentChainEntity> {
    override var entitySource: EntitySource
    var root: CompositeAbstractEntity.Builder<out CompositeAbstractEntity>?
  }

  companion object : EntityType<ParentChainEntity, Builder>() {
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
fun MutableEntityStorage.modifyParentChainEntity(
  entity: ParentChainEntity,
  modification: ParentChainEntity.Builder.() -> Unit,
): ParentChainEntity {
  return modifyEntity(ParentChainEntity.Builder::class.java, entity, modification)
}
//endregion

@Abstract
interface SimpleAbstractEntity : WorkspaceEntity {

  val parentInList: CompositeAbstractEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder<T : SimpleAbstractEntity> : WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    var parentInList: CompositeAbstractEntity.Builder<out CompositeAbstractEntity>?
  }

  companion object : EntityType<SimpleAbstractEntity, Builder<SimpleAbstractEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder<SimpleAbstractEntity>.() -> Unit)? = null,
    ): Builder<SimpleAbstractEntity> {
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
  @GeneratedCodeApiVersion(3)
  interface Builder<T : CompositeAbstractEntity> : WorkspaceEntity.Builder<T>, SimpleAbstractEntity.Builder<T> {
    override var entitySource: EntitySource
    override var parentInList: CompositeAbstractEntity.Builder<out CompositeAbstractEntity>?
    var children: List<SimpleAbstractEntity.Builder<out SimpleAbstractEntity>>
    var parentEntity: ParentChainEntity.Builder?
  }

  companion object : EntityType<CompositeAbstractEntity, Builder<CompositeAbstractEntity>>(SimpleAbstractEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder<CompositeAbstractEntity>.() -> Unit)? = null,
    ): Builder<CompositeAbstractEntity> {
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
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<CompositeChildAbstractEntity>, CompositeAbstractEntity.Builder<CompositeChildAbstractEntity> {
    override var entitySource: EntitySource
    override var parentInList: CompositeAbstractEntity.Builder<out CompositeAbstractEntity>?
    override var children: List<SimpleAbstractEntity.Builder<out SimpleAbstractEntity>>
    override var parentEntity: ParentChainEntity.Builder?
  }

  companion object : EntityType<CompositeChildAbstractEntity, Builder>(CompositeAbstractEntity) {
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
fun MutableEntityStorage.modifyCompositeChildAbstractEntity(
  entity: CompositeChildAbstractEntity,
  modification: CompositeChildAbstractEntity.Builder.() -> Unit,
): CompositeChildAbstractEntity {
  return modifyEntity(CompositeChildAbstractEntity.Builder::class.java, entity, modification)
}
//endregion

interface SimpleChildAbstractEntity : SimpleAbstractEntity {
  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SimpleChildAbstractEntity>, SimpleAbstractEntity.Builder<SimpleChildAbstractEntity> {
    override var entitySource: EntitySource
    override var parentInList: CompositeAbstractEntity.Builder<out CompositeAbstractEntity>?
  }

  companion object : EntityType<SimpleChildAbstractEntity, Builder>(SimpleAbstractEntity) {
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
fun MutableEntityStorage.modifySimpleChildAbstractEntity(
  entity: SimpleChildAbstractEntity,
  modification: SimpleChildAbstractEntity.Builder.() -> Unit,
): SimpleChildAbstractEntity {
  return modifyEntity(SimpleChildAbstractEntity.Builder::class.java, entity, modification)
}
//endregion
