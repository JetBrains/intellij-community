package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import org.jetbrains.deft.TestEntities.TestEntities




interface SampleEntity2 : WorkspaceEntity {
  val data: String
  val boolData: Boolean

  //region generated code
  //@formatter:off
  interface Builder: SampleEntity2, ModifiableWorkspaceEntity<SampleEntity2>, ObjBuilder<SampleEntity2> {
      override var data: String
      override var entitySource: EntitySource
      override var boolData: Boolean
  }
  
  companion object: ObjType<SampleEntity2, Builder>(TestEntities, 25) {
      val data: Field<SampleEntity2, String> = Field(this, 0, "data", TString)
      val entitySource: Field<SampleEntity2, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
      val boolData: Field<SampleEntity2, Boolean> = Field(this, 0, "boolData", TBoolean)
  }
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
  
  companion object: ObjType<VFUEntity2, Builder>(TestEntities, 26) {
      val data: Field<VFUEntity2, String> = Field(this, 0, "data", TString)
      val entitySource: Field<VFUEntity2, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
      val filePath: Field<VFUEntity2, VirtualFileUrl?> = Field(this, 0, "filePath", TOptional(TBlob("VirtualFileUrl")))
      val directoryPath: Field<VFUEntity2, VirtualFileUrl> = Field(this, 0, "directoryPath", TBlob("VirtualFileUrl"))
      val notNullRoots: Field<VFUEntity2, List<VirtualFileUrl>> = Field(this, 0, "notNullRoots", TList(TBlob("VirtualFileUrl")))
  }
  //@formatter:on
  //endregion

}