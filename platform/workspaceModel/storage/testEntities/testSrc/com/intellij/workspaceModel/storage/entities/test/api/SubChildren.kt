package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child


interface ParentSubEntity : WorkspaceEntity {
  val parentData: String

  @Child
  val child: ChildSubEntity


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ParentSubEntity, ModifiableWorkspaceEntity<ParentSubEntity>, ObjBuilder<ParentSubEntity> {
      override var parentData: String
      override var entitySource: EntitySource
      override var child: ChildSubEntity
  }
  
  companion object: Type<ParentSubEntity, Builder>() {
      operator fun invoke(parentData: String, entitySource: EntitySource, init: Builder.() -> Unit): ParentSubEntity {
          val builder = builder(init)
          builder.parentData = parentData
          builder.entitySource = entitySource
          return builder
      }
  }
  //@formatter:on
  //endregion

}

interface ChildSubEntity : WorkspaceEntity {
  val parentEntity: ParentSubEntity

  @Child
  val child: ChildSubSubEntity


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ChildSubEntity, ModifiableWorkspaceEntity<ChildSubEntity>, ObjBuilder<ChildSubEntity> {
      override var parentEntity: ParentSubEntity
      override var entitySource: EntitySource
      override var child: ChildSubSubEntity
  }
  
  companion object: Type<ChildSubEntity, Builder>() {
      operator fun invoke(entitySource: EntitySource, init: Builder.() -> Unit): ChildSubEntity {
          val builder = builder(init)
          builder.entitySource = entitySource
          return builder
      }
  }
  //@formatter:on
  //endregion

}

interface ChildSubSubEntity : WorkspaceEntity {
  val parentEntity: ChildSubEntity

  val childData: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ChildSubSubEntity, ModifiableWorkspaceEntity<ChildSubSubEntity>, ObjBuilder<ChildSubSubEntity> {
      override var parentEntity: ChildSubEntity
      override var entitySource: EntitySource
      override var childData: String
  }
  
  companion object: Type<ChildSubSubEntity, Builder>() {
      operator fun invoke(entitySource: EntitySource, childData: String, init: Builder.() -> Unit): ChildSubSubEntity {
          val builder = builder(init)
          builder.entitySource = entitySource
          builder.childData = childData
          return builder
      }
  }
  //@formatter:on
  //endregion

}
