package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage




interface VFUEntity : WorkspaceEntity {
  val data: String
  val fileProperty: VirtualFileUrl

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: VFUEntity, ModifiableWorkspaceEntity<VFUEntity>, ObjBuilder<VFUEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var fileProperty: VirtualFileUrl
  }
  
  companion object: Type<VFUEntity, Builder>() {
      operator fun invoke(data: String, fileProperty: VirtualFileUrl, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): VFUEntity {
          val builder = builder()
          builder.data = data
          builder.entitySource = entitySource
          builder.fileProperty = fileProperty
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: VFUEntity, modification: VFUEntity.Builder.() -> Unit) = modifyEntity(VFUEntity.Builder::class.java, entity, modification)
//endregion

interface VFUWithTwoPropertiesEntity : WorkspaceEntity {
  val data: String
  val fileProperty: VirtualFileUrl
  val secondFileProperty: VirtualFileUrl


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: VFUWithTwoPropertiesEntity, ModifiableWorkspaceEntity<VFUWithTwoPropertiesEntity>, ObjBuilder<VFUWithTwoPropertiesEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var fileProperty: VirtualFileUrl
      override var secondFileProperty: VirtualFileUrl
  }
  
  companion object: Type<VFUWithTwoPropertiesEntity, Builder>() {
      operator fun invoke(data: String, fileProperty: VirtualFileUrl, secondFileProperty: VirtualFileUrl, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): VFUWithTwoPropertiesEntity {
          val builder = builder()
          builder.data = data
          builder.entitySource = entitySource
          builder.fileProperty = fileProperty
          builder.secondFileProperty = secondFileProperty
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: VFUWithTwoPropertiesEntity, modification: VFUWithTwoPropertiesEntity.Builder.() -> Unit) = modifyEntity(VFUWithTwoPropertiesEntity.Builder::class.java, entity, modification)
//endregion

interface NullableVFUEntity : WorkspaceEntity {
  val data: String
  val fileProperty: VirtualFileUrl?


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: NullableVFUEntity, ModifiableWorkspaceEntity<NullableVFUEntity>, ObjBuilder<NullableVFUEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var fileProperty: VirtualFileUrl?
  }
  
  companion object: Type<NullableVFUEntity, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): NullableVFUEntity {
          val builder = builder()
          builder.data = data
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: NullableVFUEntity, modification: NullableVFUEntity.Builder.() -> Unit) = modifyEntity(NullableVFUEntity.Builder::class.java, entity, modification)
//endregion

interface ListVFUEntity : WorkspaceEntity {
  val data: String
  val fileProperty: List<VirtualFileUrl>


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: ListVFUEntity, ModifiableWorkspaceEntity<ListVFUEntity>, ObjBuilder<ListVFUEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var fileProperty: List<VirtualFileUrl>
  }
  
  companion object: Type<ListVFUEntity, Builder>() {
      operator fun invoke(data: String, fileProperty: List<VirtualFileUrl>, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ListVFUEntity {
          val builder = builder()
          builder.data = data
          builder.entitySource = entitySource
          builder.fileProperty = fileProperty
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: ListVFUEntity, modification: ListVFUEntity.Builder.() -> Unit) = modifyEntity(ListVFUEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addVFUEntity(
  data: String,
  fileUrl: String,
  virtualFileManager: VirtualFileUrlManager,
  source: EntitySource = SampleEntitySource("test")
): VFUEntity {
  val vfuEntity = VFUEntity(data, virtualFileManager.fromUrl(fileUrl), source)
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
  val vfuWithTwoPropertiesEntity = VFUWithTwoPropertiesEntity(data, virtualFileManager.fromUrl(fileUrl), virtualFileManager.fromUrl(secondFileUrl), source)
  this.addEntity(vfuWithTwoPropertiesEntity)
  return vfuWithTwoPropertiesEntity
}

fun MutableEntityStorage.addNullableVFUEntity(
  data: String,
  fileUrl: String?,
  virtualFileManager: VirtualFileUrlManager,
  source: EntitySource = SampleEntitySource("test")
): NullableVFUEntity {
  val nullableVFUEntity = NullableVFUEntity(data, source) {
    this.fileProperty = fileUrl?.let { virtualFileManager.fromUrl(it) }
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
  val listVFUEntity = ListVFUEntity(data, fileUrl.map { virtualFileManager.fromUrl(it) }, source)
  this.addEntity(listVFUEntity)
  return listVFUEntity
}
