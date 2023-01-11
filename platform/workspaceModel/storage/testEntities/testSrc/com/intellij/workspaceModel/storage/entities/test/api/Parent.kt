package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

import com.intellij.workspaceModel.storage.MutableEntityStorage




interface XParentEntity : WorkspaceEntity {
  val parentProperty: String

  val children: List<@Child XChildEntity>
  val optionalChildren: List<@Child XChildWithOptionalParentEntity>
  val childChild: List<@Child XChildChildEntity>

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : XParentEntity, WorkspaceEntity.Builder<XParentEntity>, ObjBuilder<XParentEntity> {
    override var entitySource: EntitySource
    override var parentProperty: String
    override var children: List<XChildEntity>
    override var optionalChildren: List<XChildWithOptionalParentEntity>
    override var childChild: List<XChildChildEntity>
  }

  companion object : Type<XParentEntity, Builder>() {
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
  @GeneratedCodeApiVersion(1)
  interface Builder : XChildEntity, WorkspaceEntity.Builder<XChildEntity>, ObjBuilder<XChildEntity> {
    override var entitySource: EntitySource
    override var childProperty: String
    override var dataClass: DataClassX?
    override var parentEntity: XParentEntity
    override var childChild: List<XChildChildEntity>
  }

  companion object : Type<XChildEntity, Builder>() {
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
  @GeneratedCodeApiVersion(1)
  interface Builder : XChildWithOptionalParentEntity, WorkspaceEntity.Builder<XChildWithOptionalParentEntity>, ObjBuilder<XChildWithOptionalParentEntity> {
    override var entitySource: EntitySource
    override var childProperty: String
    override var optionalParent: XParentEntity?
  }

  companion object : Type<XChildWithOptionalParentEntity, Builder>() {
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
  @GeneratedCodeApiVersion(1)
  interface Builder : XChildChildEntity, WorkspaceEntity.Builder<XChildChildEntity>, ObjBuilder<XChildChildEntity> {
    override var entitySource: EntitySource
    override var parent1: XParentEntity
    override var parent2: XChildEntity
  }

  companion object : Type<XChildChildEntity, Builder>() {
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
