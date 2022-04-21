package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity



interface MainEntityParentList : WorkspaceEntity {
  val x: String
  val children: List<@Child AttachedEntityParentList>


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: MainEntityParentList, ModifiableWorkspaceEntity<MainEntityParentList>, ObjBuilder<MainEntityParentList> {
      override var x: String
      override var entitySource: EntitySource
      override var children: List<AttachedEntityParentList>
  }
  
  companion object: Type<MainEntityParentList, Builder>()
  //@formatter:on
  //endregion

}

interface AttachedEntityParentList : WorkspaceEntity {
  val data: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: AttachedEntityParentList, ModifiableWorkspaceEntity<AttachedEntityParentList>, ObjBuilder<AttachedEntityParentList> {
      override var data: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<AttachedEntityParentList, Builder>()
  //@formatter:on
  //endregion

}

val AttachedEntityParentList.ref: MainEntityParentList?
  get() = referrersy(MainEntityParentList::children).singleOrNull()
