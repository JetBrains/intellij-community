package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child


interface ParentMultipleEntity : WorkspaceEntity {
  val parentData: String
  val children: List<@Child ChildMultipleEntity>


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ParentMultipleEntity, ModifiableWorkspaceEntity<ParentMultipleEntity>, ObjBuilder<ParentMultipleEntity> {
      override var parentData: String
      override var entitySource: EntitySource
      override var children: List<ChildMultipleEntity>
  }
  
  companion object: Type<ParentMultipleEntity, Builder>()
  //@formatter:on
  //endregion

}

interface ChildMultipleEntity : WorkspaceEntity {
  val childData: String

  val parentEntity: ParentMultipleEntity


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ChildMultipleEntity, ModifiableWorkspaceEntity<ChildMultipleEntity>, ObjBuilder<ChildMultipleEntity> {
      override var childData: String
      override var entitySource: EntitySource
      override var parentEntity: ParentMultipleEntity
  }
  
  companion object: Type<ChildMultipleEntity, Builder>()
  //@formatter:on
  //endregion

}