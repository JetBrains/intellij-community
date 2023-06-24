// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.WorkspaceEntity
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion

import com.intellij.platform.workspace.storage.MutableEntityStorage




interface XParentEntity : WorkspaceEntity {
  val parentProperty: String

  val children: List<@Child XChildEntity>
  val optionalChildren: List<@Child XChildWithOptionalParentEntity>
  val childChild: List<@Child XChildChildEntity>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : XParentEntity, WorkspaceEntity.Builder<XParentEntity> {
    override var entitySource: EntitySource
    override var parentProperty: String
    override var children: List<XChildEntity>
    override var optionalChildren: List<XChildWithOptionalParentEntity>
    override var childChild: List<XChildChildEntity>
  }

  companion object : EntityType<XParentEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(parentProperty: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): XParentEntity {
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
fun MutableEntityStorage.modifyEntity(entity: XParentEntity, modification: XParentEntity.Builder.() -> Unit) = modifyEntity(
  XParentEntity.Builder::class.java, entity, modification)
//endregion

data class DataClassX(val stringProperty: String, val parent: EntityReference<XParentEntity>)

interface XChildEntity : WorkspaceEntity {
  val childProperty: String
  val dataClass: DataClassX?

  val parentEntity: XParentEntity

  val childChild: List<@Child XChildChildEntity>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : XChildEntity, WorkspaceEntity.Builder<XChildEntity> {
    override var entitySource: EntitySource
    override var childProperty: String
    override var dataClass: DataClassX?
    override var parentEntity: XParentEntity
    override var childChild: List<XChildChildEntity>
  }

  companion object : EntityType<XChildEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(childProperty: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): XChildEntity {
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
fun MutableEntityStorage.modifyEntity(entity: XChildEntity, modification: XChildEntity.Builder.() -> Unit) = modifyEntity(
  XChildEntity.Builder::class.java, entity, modification)
//endregion

interface XChildWithOptionalParentEntity : WorkspaceEntity {
  val childProperty: String
  val optionalParent: XParentEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : XChildWithOptionalParentEntity, WorkspaceEntity.Builder<XChildWithOptionalParentEntity> {
    override var entitySource: EntitySource
    override var childProperty: String
    override var optionalParent: XParentEntity?
  }

  companion object : EntityType<XChildWithOptionalParentEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(childProperty: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): XChildWithOptionalParentEntity {
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
fun MutableEntityStorage.modifyEntity(entity: XChildWithOptionalParentEntity,
                                      modification: XChildWithOptionalParentEntity.Builder.() -> Unit) = modifyEntity(
  XChildWithOptionalParentEntity.Builder::class.java, entity, modification)
//endregion

interface XChildChildEntity : WorkspaceEntity {
  val parent1: XParentEntity
  val parent2: XChildEntity

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : XChildChildEntity, WorkspaceEntity.Builder<XChildChildEntity> {
    override var entitySource: EntitySource
    override var parent1: XParentEntity
    override var parent2: XChildEntity
  }

  companion object : EntityType<XChildChildEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): XChildChildEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: XChildChildEntity, modification: XChildChildEntity.Builder.() -> Unit) = modifyEntity(
  XChildChildEntity.Builder::class.java, entity, modification)
//endregion
