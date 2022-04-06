package com.intellij.workspaceModel.storage.entities.api

import com.intellij.workspaceModel.storage.EntityReference
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*





interface XParentEntity : WorkspaceEntity {
  val parentProperty: String

  val children: List<@Child XChildEntity>
  val optionalChildren: List<@Child XChildWithOptionalParentEntity>
  val childChild: List<@Child XChildChildEntity>

  //region generated code
  //@formatter:off
  interface Builder: XParentEntity, ObjBuilder<XParentEntity> {
      override var parentProperty: String
      override var entitySource: EntitySource
      override var children: List<XChildEntity>
      override var optionalChildren: List<XChildWithOptionalParentEntity>
      override var childChild: List<XChildChildEntity>
  }
  
  companion object: ObjType<XParentEntity, Builder>(IntellijWs, 50) {
      val parentProperty: Field<XParentEntity, String> = Field(this, 0, "parentProperty", TString)
      val entitySource: Field<XParentEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
      val children: Field<XParentEntity, List<XChildEntity>> = Field(this, 0, "children", TList(TRef("org.jetbrains.deft.IntellijWs", 51, child = true)))
      val optionalChildren: Field<XParentEntity, List<XChildWithOptionalParentEntity>> = Field(this, 0, "optionalChildren", TList(TRef("org.jetbrains.deft.IntellijWs", 52, child = true)))
      val childChild: Field<XParentEntity, List<XChildChildEntity>> = Field(this, 0, "childChild", TList(TRef("org.jetbrains.deft.IntellijWs", 53, child = true)))
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
  interface Builder: XChildEntity, ObjBuilder<XChildEntity> {
      override var childProperty: String
      override var entitySource: EntitySource
      override var dataClass: DataClassX?
      override var parentEntity: XParentEntity
      override var childChild: List<XChildChildEntity>
  }
  
  companion object: ObjType<XChildEntity, Builder>(IntellijWs, 51) {
      val childProperty: Field<XChildEntity, String> = Field(this, 0, "childProperty", TString)
      val entitySource: Field<XChildEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
      val dataClass: Field<XChildEntity, DataClassX?> = Field(this, 0, "dataClass", TOptional(TBlob("DataClassX")))
      val parentEntity: Field<XChildEntity, XParentEntity> = Field(this, 0, "parentEntity", TRef("org.jetbrains.deft.IntellijWs", 50))
      val childChild: Field<XChildEntity, List<XChildChildEntity>> = Field(this, 0, "childChild", TList(TRef("org.jetbrains.deft.IntellijWs", 53, child = true)))
  }
  //@formatter:on
  //endregion

}

interface XChildWithOptionalParentEntity : WorkspaceEntity {
  val childProperty: String
  val optionalParent: XParentEntity?

  //region generated code
  //@formatter:off
  interface Builder: XChildWithOptionalParentEntity, ObjBuilder<XChildWithOptionalParentEntity> {
      override var childProperty: String
      override var entitySource: EntitySource
      override var optionalParent: XParentEntity?
  }
  
  companion object: ObjType<XChildWithOptionalParentEntity, Builder>(IntellijWs, 52) {
      val childProperty: Field<XChildWithOptionalParentEntity, String> = Field(this, 0, "childProperty", TString)
      val entitySource: Field<XChildWithOptionalParentEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
      val optionalParent: Field<XChildWithOptionalParentEntity, XParentEntity?> = Field(this, 0, "optionalParent", TOptional(TRef("org.jetbrains.deft.IntellijWs", 50)))
  }
  //@formatter:on
  //endregion

}

interface XChildChildEntity : WorkspaceEntity {
  val parent1: XParentEntity
  val parent2: XChildEntity
  //region generated code
  //@formatter:off
  interface Builder: XChildChildEntity, ObjBuilder<XChildChildEntity> {
      override var parent1: XParentEntity
      override var entitySource: EntitySource
      override var parent2: XChildEntity
  }
  
  companion object: ObjType<XChildChildEntity, Builder>(IntellijWs, 53) {
      val parent1: Field<XChildChildEntity, XParentEntity> = Field(this, 0, "parent1", TRef("org.jetbrains.deft.IntellijWs", 50))
      val entitySource: Field<XChildChildEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
      val parent2: Field<XChildChildEntity, XChildEntity> = Field(this, 0, "parent2", TRef("org.jetbrains.deft.IntellijWs", 51))
  }
  //@formatter:on
  //endregion

}