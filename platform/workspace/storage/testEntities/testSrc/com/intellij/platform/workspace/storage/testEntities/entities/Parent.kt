// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child


interface XParentEntity : WorkspaceEntity {
  val parentProperty: String

  val children: List<@Child XChildEntity>
  val optionalChildren: List<@Child XChildWithOptionalParentEntity>
  val childChild: List<@Child XChildChildEntity>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<XParentEntity> {
    override var entitySource: EntitySource
    var parentProperty: String
    var children: List<XChildEntity.Builder>
    var optionalChildren: List<XChildWithOptionalParentEntity.Builder>
    var childChild: List<XChildChildEntity.Builder>
  }

  companion object : EntityType<XParentEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      parentProperty: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.parentProperty = parentProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyXParentEntity(
  entity: XParentEntity,
  modification: XParentEntity.Builder.() -> Unit,
): XParentEntity {
  return modifyEntity(XParentEntity.Builder::class.java, entity, modification)
}
//endregion

data class DataClassX(val stringProperty: String, val parent: EntityPointer<XParentEntity>)

interface XChildEntity : WorkspaceEntity {
  val childProperty: String
  val dataClass: DataClassX?

  val parentEntity: XParentEntity

  val childChild: List<@Child XChildChildEntity>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<XChildEntity> {
    override var entitySource: EntitySource
    var childProperty: String
    var dataClass: DataClassX?
    var parentEntity: XParentEntity.Builder
    var childChild: List<XChildChildEntity.Builder>
  }

  companion object : EntityType<XChildEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      childProperty: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.childProperty = childProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyXChildEntity(
  entity: XChildEntity,
  modification: XChildEntity.Builder.() -> Unit,
): XChildEntity {
  return modifyEntity(XChildEntity.Builder::class.java, entity, modification)
}
//endregion

interface XChildWithOptionalParentEntity : WorkspaceEntity {
  val childProperty: String
  val optionalParent: XParentEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<XChildWithOptionalParentEntity> {
    override var entitySource: EntitySource
    var childProperty: String
    var optionalParent: XParentEntity.Builder?
  }

  companion object : EntityType<XChildWithOptionalParentEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      childProperty: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.childProperty = childProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyXChildWithOptionalParentEntity(
  entity: XChildWithOptionalParentEntity,
  modification: XChildWithOptionalParentEntity.Builder.() -> Unit,
): XChildWithOptionalParentEntity {
  return modifyEntity(XChildWithOptionalParentEntity.Builder::class.java, entity, modification)
}
//endregion

interface XChildChildEntity : WorkspaceEntity {
  val parent1: XParentEntity
  val parent2: XChildEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<XChildChildEntity> {
    override var entitySource: EntitySource
    var parent1: XParentEntity.Builder
    var parent2: XChildEntity.Builder
  }

  companion object : EntityType<XChildChildEntity, Builder>() {
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
fun MutableEntityStorage.modifyXChildChildEntity(
  entity: XChildChildEntity,
  modification: XChildChildEntity.Builder.() -> Unit,
): XChildChildEntity {
  return modifyEntity(XChildChildEntity.Builder::class.java, entity, modification)
}
//endregion
