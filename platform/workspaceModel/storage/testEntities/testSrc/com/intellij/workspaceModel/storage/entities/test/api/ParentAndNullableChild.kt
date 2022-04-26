package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child


interface ParentNullableEntity : WorkspaceEntity {
  val parentData: String

  @Child
  val child: ChildNullableEntity?


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ParentNullableEntity, ModifiableWorkspaceEntity<ParentNullableEntity>, ObjBuilder<ParentNullableEntity> {
      override var parentData: String
      override var entitySource: EntitySource
      override var child: ChildNullableEntity?
  }
  
  companion object: Type<ParentNullableEntity, Builder>() {
      operator fun invoke(parentData: String, entitySource: EntitySource, init: Builder.() -> Unit): ParentNullableEntity {
          val builder = builder(init)
          builder.parentData = parentData
          builder.entitySource = entitySource
          return builder
      }
  }
  //@formatter:on
  //endregion

}

interface ChildNullableEntity : WorkspaceEntity {
  val childData: String

  val parentEntity: ParentNullableEntity


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ChildNullableEntity, ModifiableWorkspaceEntity<ChildNullableEntity>, ObjBuilder<ChildNullableEntity> {
      override var childData: String
      override var entitySource: EntitySource
      override var parentEntity: ParentNullableEntity
  }
  
  companion object: Type<ChildNullableEntity, Builder>() {
      operator fun invoke(childData: String, entitySource: EntitySource, init: Builder.() -> Unit): ChildNullableEntity {
          val builder = builder(init)
          builder.childData = childData
          builder.entitySource = entitySource
          return builder
      }
  }
  //@formatter:on
  //endregion

}
