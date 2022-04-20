package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntityReference
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity

import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion



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
  
  companion object: Type<XParentEntity, Builder>()
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
  
  companion object: Type<XChildEntity, Builder>()
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
  
  companion object: Type<XChildWithOptionalParentEntity, Builder>()
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
  
  companion object: Type<XChildChildEntity, Builder>()
  //@formatter:on
  //endregion

}