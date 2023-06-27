// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child


interface HeadAbstractionEntity : WorkspaceEntityWithSymbolicId {
  val data: String
  val child: @Child CompositeBaseEntity?

  override val symbolicId: SymbolicEntityId<WorkspaceEntityWithSymbolicId>
    get() = HeadAbstractionSymbolicId(data)

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : HeadAbstractionEntity, WorkspaceEntity.Builder<HeadAbstractionEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var child: CompositeBaseEntity?
  }

  companion object : EntityType<HeadAbstractionEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): HeadAbstractionEntity {
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
fun MutableEntityStorage.modifyEntity(entity: HeadAbstractionEntity, modification: HeadAbstractionEntity.Builder.() -> Unit) = modifyEntity(
  HeadAbstractionEntity.Builder::class.java, entity, modification)
//endregion

data class HeadAbstractionSymbolicId(override val presentableName: String) : SymbolicEntityId<HeadAbstractionEntity>


@Abstract
interface BaseEntity : WorkspaceEntity {
  val parentEntity: CompositeBaseEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder<T : BaseEntity> : BaseEntity, WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    override var parentEntity: CompositeBaseEntity?
  }

  companion object : EntityType<BaseEntity, Builder<BaseEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder<BaseEntity>.() -> Unit)? = null): BaseEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

@Abstract
interface CompositeBaseEntity : BaseEntity {
  val children: List<@Child BaseEntity>

  val parent: HeadAbstractionEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder<T : CompositeBaseEntity> : CompositeBaseEntity, BaseEntity.Builder<T>, WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    override var parentEntity: CompositeBaseEntity?
    override var children: List<BaseEntity>
    override var parent: HeadAbstractionEntity?
  }

  companion object : EntityType<CompositeBaseEntity, Builder<CompositeBaseEntity>>(BaseEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder<CompositeBaseEntity>.() -> Unit)? = null): CompositeBaseEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

interface MiddleEntity : BaseEntity {
  val property: String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : MiddleEntity, BaseEntity.Builder<MiddleEntity>, WorkspaceEntity.Builder<MiddleEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositeBaseEntity?
    override var property: String
  }

  companion object : EntityType<MiddleEntity, Builder>(BaseEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(property: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): MiddleEntity {
      val builder = builder()
      builder.property = property
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: MiddleEntity, modification: MiddleEntity.Builder.() -> Unit) = modifyEntity(
  MiddleEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addMiddleEntity(property: String = "prop", source: EntitySource = MySource): MiddleEntity {
  val middleEntity = MiddleEntity(property, source)
  this.addEntity(middleEntity)
  return middleEntity
}

// ---------------------------

interface LeftEntity : CompositeBaseEntity {
  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : LeftEntity, CompositeBaseEntity.Builder<LeftEntity>, WorkspaceEntity.Builder<LeftEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositeBaseEntity?
    override var children: List<BaseEntity>
    override var parent: HeadAbstractionEntity?
  }

  companion object : EntityType<LeftEntity, Builder>(CompositeBaseEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): LeftEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: LeftEntity, modification: LeftEntity.Builder.() -> Unit) = modifyEntity(
  LeftEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addLeftEntity(children: Sequence<BaseEntity>, source: EntitySource = MySource): LeftEntity {
  val leftEntity = LeftEntity(source) {
    this.children = children.toList()
  }
  this.addEntity(leftEntity)
  return leftEntity
}

// ---------------------------

interface RightEntity : CompositeBaseEntity {
  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : RightEntity, CompositeBaseEntity.Builder<RightEntity>, WorkspaceEntity.Builder<RightEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositeBaseEntity?
    override var children: List<BaseEntity>
    override var parent: HeadAbstractionEntity?
  }

  companion object : EntityType<RightEntity, Builder>(CompositeBaseEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): RightEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: RightEntity, modification: RightEntity.Builder.() -> Unit) = modifyEntity(
  RightEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addRightEntity(children: Sequence<BaseEntity>, source: EntitySource = MySource): RightEntity {
  val rightEntity = RightEntity(source) {
    this.children = children.toList()
  }
  this.addEntity(rightEntity)
  return rightEntity
}
