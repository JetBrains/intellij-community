// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type


interface BooleanEntity : WorkspaceEntity {
  val data: Boolean
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: BooleanEntity, ModifiableWorkspaceEntity<BooleanEntity>, ObjBuilder<BooleanEntity> {
      override var data: Boolean
      override var entitySource: EntitySource
  }
  
  companion object: Type<BooleanEntity, Builder>()
  //@formatter:on
  //endregion

}

interface IntEntity : WorkspaceEntity {
  val data: Int
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: IntEntity, ModifiableWorkspaceEntity<IntEntity>, ObjBuilder<IntEntity> {
      override var data: Int
      override var entitySource: EntitySource
  }
  
  companion object: Type<IntEntity, Builder>()
  //@formatter:on
  //endregion

}

interface StringEntity : WorkspaceEntity {
  val data: String
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: StringEntity, ModifiableWorkspaceEntity<StringEntity>, ObjBuilder<StringEntity> {
      override var data: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<StringEntity, Builder>()
  //@formatter:on
  //endregion

}

interface ListEntity : WorkspaceEntity {
  val data: List<String>
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ListEntity, ModifiableWorkspaceEntity<ListEntity>, ObjBuilder<ListEntity> {
      override var data: List<String>
      override var entitySource: EntitySource
  }
  
  companion object: Type<ListEntity, Builder>()
  //@formatter:on
  //endregion

}


interface OptionalIntEntity : WorkspaceEntity {
  val data: Int?
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: OptionalIntEntity, ModifiableWorkspaceEntity<OptionalIntEntity>, ObjBuilder<OptionalIntEntity> {
      override var data: Int?
      override var entitySource: EntitySource
  }
  
  companion object: Type<OptionalIntEntity, Builder>()
  //@formatter:on
  //endregion

}


interface OptionalStringEntity : WorkspaceEntity {
  val data: String?
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: OptionalStringEntity, ModifiableWorkspaceEntity<OptionalStringEntity>, ObjBuilder<OptionalStringEntity> {
      override var data: String?
      override var entitySource: EntitySource
  }
  
  companion object: Type<OptionalStringEntity, Builder>()
  //@formatter:on
  //endregion

}

// Well, they work not that good
/*
interface OptionalListIntEntity : WorkspaceEntity {
  val data: List<Int>?
}
*/
