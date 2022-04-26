package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity



interface VFUEntity : WorkspaceEntity {
  val data: String
  val fileProperty: VirtualFileUrl

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: VFUEntity, ModifiableWorkspaceEntity<VFUEntity>, ObjBuilder<VFUEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var fileProperty: VirtualFileUrl
  }
  
  companion object: Type<VFUEntity, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, fileProperty: VirtualFileUrl, init: Builder.() -> Unit): VFUEntity {
          val builder = builder(init)
          builder.data = data
          builder.entitySource = entitySource
          builder.fileProperty = fileProperty
          return builder
      }
  }
  //@formatter:on
  //endregion

}

interface VFUWithTwoPropertiesEntity : WorkspaceEntity {
  val data: String
  val fileProperty: VirtualFileUrl
  val secondFileProperty: VirtualFileUrl


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: VFUWithTwoPropertiesEntity, ModifiableWorkspaceEntity<VFUWithTwoPropertiesEntity>, ObjBuilder<VFUWithTwoPropertiesEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var fileProperty: VirtualFileUrl
      override var secondFileProperty: VirtualFileUrl
  }
  
  companion object: Type<VFUWithTwoPropertiesEntity, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, fileProperty: VirtualFileUrl, secondFileProperty: VirtualFileUrl, init: Builder.() -> Unit): VFUWithTwoPropertiesEntity {
          val builder = builder(init)
          builder.data = data
          builder.entitySource = entitySource
          builder.fileProperty = fileProperty
          builder.secondFileProperty = secondFileProperty
          return builder
      }
  }
  //@formatter:on
  //endregion

}

interface NullableVFUEntity : WorkspaceEntity {
  val data: String
  val fileProperty: VirtualFileUrl?


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: NullableVFUEntity, ModifiableWorkspaceEntity<NullableVFUEntity>, ObjBuilder<NullableVFUEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var fileProperty: VirtualFileUrl?
  }
  
  companion object: Type<NullableVFUEntity, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, init: Builder.() -> Unit): NullableVFUEntity {
          val builder = builder(init)
          builder.data = data
          builder.entitySource = entitySource
          return builder
      }
  }
  //@formatter:on
  //endregion

}

interface ListVFUEntity : WorkspaceEntity {
  val data: String
  val fileProperty: List<VirtualFileUrl>


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ListVFUEntity, ModifiableWorkspaceEntity<ListVFUEntity>, ObjBuilder<ListVFUEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var fileProperty: List<VirtualFileUrl>
  }
  
  companion object: Type<ListVFUEntity, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, fileProperty: List<VirtualFileUrl>, init: Builder.() -> Unit): ListVFUEntity {
          val builder = builder(init)
          builder.data = data
          builder.entitySource = entitySource
          builder.fileProperty = fileProperty
          return builder
      }
  }
  //@formatter:on
  //endregion

}

fun MutableEntityStorage.addVFUEntity(
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

fun MutableEntityStorage.addVFU2Entity(
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

fun MutableEntityStorage.addNullableVFUEntity(
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

fun MutableEntityStorage.addListVFUEntity(
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
