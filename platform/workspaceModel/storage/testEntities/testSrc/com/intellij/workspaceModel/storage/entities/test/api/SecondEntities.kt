package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type


interface SampleEntity2 : WorkspaceEntity {
  val data: String
  val boolData: Boolean
  val optionalData: String?

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: SampleEntity2, ModifiableWorkspaceEntity<SampleEntity2>, ObjBuilder<SampleEntity2> {
      override var data: String
      override var entitySource: EntitySource
      override var boolData: Boolean
      override var optionalData: String?
  }
  
  companion object: Type<SampleEntity2, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, boolData: Boolean, init: (Builder.() -> Unit)? = null): SampleEntity2 {
          val builder = builder()
          builder.data = data
          builder.entitySource = entitySource
          builder.boolData = boolData
          init?.invoke(builder)
          return builder
      }
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
  @GeneratedCodeApiVersion(0)
  interface Builder: VFUEntity2, ModifiableWorkspaceEntity<VFUEntity2>, ObjBuilder<VFUEntity2> {
      override var data: String
      override var entitySource: EntitySource
      override var filePath: VirtualFileUrl?
      override var directoryPath: VirtualFileUrl
      override var notNullRoots: List<VirtualFileUrl>
  }
  
  companion object: Type<VFUEntity2, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, directoryPath: VirtualFileUrl, notNullRoots: List<VirtualFileUrl>, init: (Builder.() -> Unit)? = null): VFUEntity2 {
          val builder = builder()
          builder.data = data
          builder.entitySource = entitySource
          builder.directoryPath = directoryPath
          builder.notNullRoots = notNullRoots
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}