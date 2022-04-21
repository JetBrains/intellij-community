package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity



interface MainEntityList : WorkspaceEntity {
  val x: String

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: MainEntityList, ModifiableWorkspaceEntity<MainEntityList>, ObjBuilder<MainEntityList> {
      override var x: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<MainEntityList, Builder>()
  //@formatter:on
  //endregion
}

interface AttachedEntityList : WorkspaceEntity {
  val ref: MainEntityList?
  val data: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: AttachedEntityList, ModifiableWorkspaceEntity<AttachedEntityList>, ObjBuilder<AttachedEntityList> {
      override var ref: MainEntityList?
      override var entitySource: EntitySource
      override var data: String
  }
  
  companion object: Type<AttachedEntityList, Builder>()
  //@formatter:on
  //endregion

}

val MainEntityList.child: List<@Child AttachedEntityList>
  get() = referrersx(AttachedEntityList::ref)
