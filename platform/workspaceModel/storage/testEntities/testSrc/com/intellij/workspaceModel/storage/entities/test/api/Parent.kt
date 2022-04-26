package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity



interface XParentEntity : WorkspaceEntity {
  val parentProperty: String

  val children: List<@Child XChildEntity>
  val optionalChildren: List<@Child XChildWithOptionalParentEntity>
  val childChild: List<@Child XChildChildEntity>


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: XParentEntity, ModifiableWorkspaceEntity<XParentEntity>, ObjBuilder<XParentEntity> {
      override var parentProperty: String
      override var entitySource: EntitySource
      override var children: List<XChildEntity>
      override var optionalChildren: List<XChildWithOptionalParentEntity>
      override var childChild: List<XChildChildEntity>
  }
  
  companion object: Type<XParentEntity, Builder>() {
      operator fun invoke(parentProperty: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): XParentEntity {
          val builder = builder()
          builder.parentProperty = parentProperty
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}

data class DataClassX(val stringProperty: String, val parent: EntityReference<XParentEntity>)

interface XChildEntity : WorkspaceEntity {
  val childProperty: String
  val dataClass: DataClassX?

  val parentEntity: XParentEntity

  val childChild: List<@Child XChildChildEntity>

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: XChildEntity, ModifiableWorkspaceEntity<XChildEntity>, ObjBuilder<XChildEntity> {
      override var childProperty: String
      override var entitySource: EntitySource
      override var dataClass: DataClassX?
      override var parentEntity: XParentEntity
      override var childChild: List<XChildChildEntity>
  }
  
  companion object: Type<XChildEntity, Builder>() {
      operator fun invoke(childProperty: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): XChildEntity {
          val builder = builder()
          builder.childProperty = childProperty
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}

interface XChildWithOptionalParentEntity : WorkspaceEntity {
  val childProperty: String
  val optionalParent: XParentEntity?

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: XChildWithOptionalParentEntity, ModifiableWorkspaceEntity<XChildWithOptionalParentEntity>, ObjBuilder<XChildWithOptionalParentEntity> {
      override var childProperty: String
      override var entitySource: EntitySource
      override var optionalParent: XParentEntity?
  }
  
  companion object: Type<XChildWithOptionalParentEntity, Builder>() {
      operator fun invoke(childProperty: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): XChildWithOptionalParentEntity {
          val builder = builder()
          builder.childProperty = childProperty
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}

interface XChildChildEntity : WorkspaceEntity {
  val parent1: XParentEntity
  val parent2: XChildEntity


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: XChildChildEntity, ModifiableWorkspaceEntity<XChildChildEntity>, ObjBuilder<XChildChildEntity> {
      override var parent1: XParentEntity
      override var entitySource: EntitySource
      override var parent2: XChildEntity
  }
  
  companion object: Type<XChildChildEntity, Builder>() {
      operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): XChildChildEntity {
          val builder = builder()
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}