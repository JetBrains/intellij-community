package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity



interface MainEntityToParent : WorkspaceEntity {
  val child: @Child AttachedEntityToParent?
  val x: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: MainEntityToParent, ModifiableWorkspaceEntity<MainEntityToParent>, ObjBuilder<MainEntityToParent> {
      override var child: AttachedEntityToParent?
      override var entitySource: EntitySource
      override var x: String
  }
  
  companion object: Type<MainEntityToParent, Builder>()
  //@formatter:on
  //endregion

}

interface AttachedEntityToParent : WorkspaceEntity {
  val data: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: AttachedEntityToParent, ModifiableWorkspaceEntity<AttachedEntityToParent>, ObjBuilder<AttachedEntityToParent> {
      override var data: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<AttachedEntityToParent, Builder>()
  //@formatter:on
  //endregion

}

val AttachedEntityToParent.ref: MainEntityToParent
  get() = referrersx(MainEntityToParent::child).single()
