// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child


interface HeadAbstractionEntity : WorkspaceEntityWithSymbolicId {
  val data: String
  val child: @Child CompositeBaseEntity?

  override val symbolicId: HeadAbstractionSymbolicId
    get() = HeadAbstractionSymbolicId(data)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<HeadAbstractionEntity> {
    override var entitySource: EntitySource
    var data: String
    var child: CompositeBaseEntity.Builder<out CompositeBaseEntity>?
  }

  companion object : EntityType<HeadAbstractionEntity, Builder>() {
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
fun MutableEntityStorage.modifyHeadAbstractionEntity(
  entity: HeadAbstractionEntity,
  modification: HeadAbstractionEntity.Builder.() -> Unit,
): HeadAbstractionEntity {
  return modifyEntity(HeadAbstractionEntity.Builder::class.java, entity, modification)
}
//endregion

data class HeadAbstractionSymbolicId(override val presentableName: String) : SymbolicEntityId<HeadAbstractionEntity>


@Abstract
interface BaseEntity : WorkspaceEntity {
  val parentEntity: CompositeBaseEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder<T : BaseEntity> : WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    var parentEntity: CompositeBaseEntity.Builder<out CompositeBaseEntity>?
  }

  companion object : EntityType<BaseEntity, Builder<BaseEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder<BaseEntity>.() -> Unit)? = null,
    ): Builder<BaseEntity> {
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
  @GeneratedCodeApiVersion(3)
  interface Builder<T : CompositeBaseEntity> : WorkspaceEntity.Builder<T>, BaseEntity.Builder<T> {
    override var entitySource: EntitySource
    override var parentEntity: CompositeBaseEntity.Builder<out CompositeBaseEntity>?
    var children: List<BaseEntity.Builder<out BaseEntity>>
    var parent: HeadAbstractionEntity.Builder?
  }

  companion object : EntityType<CompositeBaseEntity, Builder<CompositeBaseEntity>>(BaseEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder<CompositeBaseEntity>.() -> Unit)? = null,
    ): Builder<CompositeBaseEntity> {
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
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<MiddleEntity>, BaseEntity.Builder<MiddleEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositeBaseEntity.Builder<out CompositeBaseEntity>?
    var property: String
  }

  companion object : EntityType<MiddleEntity, Builder>(BaseEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      property: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyMiddleEntity(
  entity: MiddleEntity,
  modification: MiddleEntity.Builder.() -> Unit,
): MiddleEntity {
  return modifyEntity(MiddleEntity.Builder::class.java, entity, modification)
}
//endregion

// ---------------------------

interface LeftEntity : CompositeBaseEntity {
  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<LeftEntity>, CompositeBaseEntity.Builder<LeftEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositeBaseEntity.Builder<out CompositeBaseEntity>?
    override var children: List<BaseEntity.Builder<out BaseEntity>>
    override var parent: HeadAbstractionEntity.Builder?
  }

  companion object : EntityType<LeftEntity, Builder>(CompositeBaseEntity) {
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
fun MutableEntityStorage.modifyLeftEntity(
  entity: LeftEntity,
  modification: LeftEntity.Builder.() -> Unit,
): LeftEntity {
  return modifyEntity(LeftEntity.Builder::class.java, entity, modification)
}
//endregion

// ---------------------------

interface RightEntity : CompositeBaseEntity {
  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<RightEntity>, CompositeBaseEntity.Builder<RightEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositeBaseEntity.Builder<out CompositeBaseEntity>?
    override var children: List<BaseEntity.Builder<out BaseEntity>>
    override var parent: HeadAbstractionEntity.Builder?
  }

  companion object : EntityType<RightEntity, Builder>(CompositeBaseEntity) {
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
fun MutableEntityStorage.modifyRightEntity(
  entity: RightEntity,
  modification: RightEntity.Builder.() -> Unit,
): RightEntity {
  return modifyEntity(RightEntity.Builder::class.java, entity, modification)
}
//endregion
