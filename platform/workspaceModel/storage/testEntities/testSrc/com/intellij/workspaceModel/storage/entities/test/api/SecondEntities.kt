package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*

import org.jetbrains.deft.Type





interface SampleEntity2 : WorkspaceEntity {
  val data: String
  val boolData: Boolean
  val optionalData: String?

  //region generated code
  //@formatter:off
  interface Builder: SampleEntity2, ModifiableWorkspaceEntity<SampleEntity2>, ObjBuilder<SampleEntity2> {
      override var data: String
      override var entitySource: EntitySource
      override var boolData: Boolean
      override var optionalData: String?
  }
  
  companion object: Type<SampleEntity2, Builder>(76)
  //@formatter:on
  //endregion

}

interface VFUEntity2 : WorkspaceEntity {
  val data: String
  val filePath: VirtualFileUrl?
  val directoryPath: VirtualFileUrl
  val notNullRoots: List<VirtualFileUrl>
  //region generated code
  //@formatter:off
  interface Builder: VFUEntity2, ModifiableWorkspaceEntity<VFUEntity2>, ObjBuilder<VFUEntity2> {
      override var data: String
      override var entitySource: EntitySource
      override var filePath: VirtualFileUrl?
      override var directoryPath: VirtualFileUrl
      override var notNullRoots: List<VirtualFileUrl>
  }
  
  companion object: Type<VFUEntity2, Builder>(77)
  //@formatter:on
  //endregion

}