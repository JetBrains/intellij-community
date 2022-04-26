package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child


interface ParentEntity : WorkspaceEntity {
  val parentData: String

  @Child
  val child: ChildEntity

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ParentEntity, ModifiableWorkspaceEntity<ParentEntity>, ObjBuilder<ParentEntity> {
      override var parentData: String
      override var entitySource: EntitySource
      override var child: ChildEntity
  }
  
  companion object: Type<ParentEntity, Builder>() {
      operator fun invoke(parentData: String, entitySource: EntitySource, init: Builder.() -> Unit): ParentEntity {
          val builder = builder(init)
          builder.parentData = parentData
          builder.entitySource = entitySource
          return builder
      }
  }
  //@formatter:on
  //endregion

}

interface ChildEntity : WorkspaceEntity {
  val childData: String

  //    override val parent: ParentEntity
  val parentEntity: ParentEntity


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ChildEntity, ModifiableWorkspaceEntity<ChildEntity>, ObjBuilder<ChildEntity> {
      override var childData: String
      override var entitySource: EntitySource
      override var parentEntity: ParentEntity
  }
  
  companion object: Type<ChildEntity, Builder>() {
      operator fun invoke(childData: String, entitySource: EntitySource, init: Builder.() -> Unit): ChildEntity {
          val builder = builder(init)
          builder.childData = childData
          builder.entitySource = entitySource
          return builder
      }
  }
  //@formatter:on
  //endregion

}