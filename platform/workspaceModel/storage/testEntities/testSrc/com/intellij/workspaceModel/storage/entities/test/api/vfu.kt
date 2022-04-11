package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity

import org.jetbrains.deft.Type





interface VFUEntity : WorkspaceEntity {
  val data: String
  val fileProperty: VirtualFileUrl

  //region generated code
  //@formatter:off
  interface Builder: VFUEntity, ModifiableWorkspaceEntity<VFUEntity>, ObjBuilder<VFUEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var fileProperty: VirtualFileUrl
  }
  
  companion object: Type<VFUEntity, Builder>(66)
  //@formatter:on
  //endregion

}

interface VFUWithTwoPropertiesEntity : WorkspaceEntity {
  val data: String
  val fileProperty: VirtualFileUrl
  val secondFileProperty: VirtualFileUrl



  //region generated code
  //@formatter:off
  interface Builder: VFUWithTwoPropertiesEntity, ModifiableWorkspaceEntity<VFUWithTwoPropertiesEntity>, ObjBuilder<VFUWithTwoPropertiesEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var fileProperty: VirtualFileUrl
      override var secondFileProperty: VirtualFileUrl
  }
  
  companion object: Type<VFUWithTwoPropertiesEntity, Builder>(67)
  //@formatter:on
  //endregion

}

interface NullableVFUEntity : WorkspaceEntity {
  val data: String
  val fileProperty: VirtualFileUrl?



  //region generated code
  //@formatter:off
  interface Builder: NullableVFUEntity, ModifiableWorkspaceEntity<NullableVFUEntity>, ObjBuilder<NullableVFUEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var fileProperty: VirtualFileUrl?
  }
  
  companion object: Type<NullableVFUEntity, Builder>(68)
  //@formatter:on
  //endregion

}

interface ListVFUEntity : WorkspaceEntity {
  val data: String
  val fileProperty: List<VirtualFileUrl>



  //region generated code
  //@formatter:off
  interface Builder: ListVFUEntity, ModifiableWorkspaceEntity<ListVFUEntity>, ObjBuilder<ListVFUEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var fileProperty: List<VirtualFileUrl>
  }
  
  companion object: Type<ListVFUEntity, Builder>(69)
  //@formatter:on
  //endregion

}

fun WorkspaceEntityStorageBuilder.addVFUEntity(
  data: String,
  fileUrl: String,
  virtualFileManager: VirtualFileUrlManager,
  source: EntitySource = SampleEntitySource("test")
): VFUEntity {
  val vfuEntity = VFUEntity {
    this.data = data
    this.fileProperty = virtualFileManager.fromUrl(fileUrl)
    this.entitySource = source
  }
  this.addEntity(vfuEntity)
  return vfuEntity
}

fun WorkspaceEntityStorageBuilder.addVFU2Entity(
  data: String,
  fileUrl: String,
  secondFileUrl: String,
  virtualFileManager: VirtualFileUrlManager,
  source: EntitySource = SampleEntitySource("test")
): VFUWithTwoPropertiesEntity {
  val vfuWithTwoPropertiesEntity = VFUWithTwoPropertiesEntity {
    this.entitySource = source
    this.data = data
    this.fileProperty = virtualFileManager.fromUrl(fileUrl)
    this.secondFileProperty = virtualFileManager.fromUrl(secondFileUrl)
  }
  this.addEntity(vfuWithTwoPropertiesEntity)
  return vfuWithTwoPropertiesEntity
}

fun WorkspaceEntityStorageBuilder.addNullableVFUEntity(
  data: String,
  fileUrl: String?,
  virtualFileManager: VirtualFileUrlManager,
  source: EntitySource = SampleEntitySource("test")
): NullableVFUEntity {
  val nullableVFUEntity = NullableVFUEntity {
    this.data = data
    this.fileProperty = fileUrl?.let { virtualFileManager.fromUrl(it) }
    this.entitySource = source
  }
  this.addEntity(nullableVFUEntity)
  return nullableVFUEntity
}

fun WorkspaceEntityStorageBuilder.addListVFUEntity(
  data: String,
  fileUrl: List<String>,
  virtualFileManager: VirtualFileUrlManager,
  source: EntitySource = SampleEntitySource("test")
): ListVFUEntity {
  val listVFUEntity = ListVFUEntity {
    this.data = data
    this.fileProperty = fileUrl.map { virtualFileManager.fromUrl(it) }
    this.entitySource = source
  }
  this.addEntity(listVFUEntity)
  return listVFUEntity
}
