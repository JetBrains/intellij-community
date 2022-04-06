// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entitiesx

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.SampleEntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.EntityDataDelegation
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlListProperty
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlNullableProperty
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlProperty
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

// ---------------------------------------


class VFUEntityData : WorkspaceEntityData<VFUEntity>() {
  lateinit var data: String
  lateinit var fileProperty: VirtualFileUrl
  override fun createEntity(snapshot: WorkspaceEntityStorage): VFUEntity {
    return VFUEntity(data, fileProperty).also { addMetaData(it, snapshot) }
  }
}

class VFUWithTwoPropertiesEntityData : WorkspaceEntityData<VFUWithTwoPropertiesEntity>() {
  lateinit var data: String
  lateinit var fileProperty: VirtualFileUrl
  lateinit var secondFileProperty: VirtualFileUrl
  override fun createEntity(snapshot: WorkspaceEntityStorage): VFUWithTwoPropertiesEntity {
    return VFUWithTwoPropertiesEntity(data, fileProperty, secondFileProperty).also { addMetaData(it, snapshot) }
  }
}

class NullableVFUEntityData : WorkspaceEntityData<NullableVFUEntity>() {
  lateinit var data: String
  var fileProperty: VirtualFileUrl? = null
  override fun createEntity(snapshot: WorkspaceEntityStorage): NullableVFUEntity {
    return NullableVFUEntity(data, fileProperty).also { addMetaData(it, snapshot) }
  }
}

class ListVFUEntityData : WorkspaceEntityData<ListVFUEntity>() {
  lateinit var data: String
  lateinit var fileProperty: List<VirtualFileUrl>
  override fun createEntity(snapshot: WorkspaceEntityStorage): ListVFUEntity {
    return ListVFUEntity(data, fileProperty).also { addMetaData(it, snapshot) }
  }
}

class VFUEntity(val data: String, val fileProperty: VirtualFileUrl) : WorkspaceEntityBase()
class VFUWithTwoPropertiesEntity(val data: String,
                                 val fileProperty: VirtualFileUrl,
                                 val secondFileProperty: VirtualFileUrl) : WorkspaceEntityBase()

class NullableVFUEntity(val data: String, val fileProperty: VirtualFileUrl?) : WorkspaceEntityBase()
class ListVFUEntity(val data: String, val fileProperty: List<VirtualFileUrl>) : WorkspaceEntityBase()

class ModifiableVFUEntity : ModifiableWorkspaceEntityBase<VFUEntity>() {
  var data: String by EntityDataDelegation()
  var fileProperty: VirtualFileUrl by VirtualFileUrlProperty()
}

class ModifiableVFUWithTwoPropertiesEntity : ModifiableWorkspaceEntityBase<VFUWithTwoPropertiesEntity>() {
  var data: String by EntityDataDelegation()
  var fileProperty: VirtualFileUrl by VirtualFileUrlProperty()
  var secondFileProperty: VirtualFileUrl by VirtualFileUrlProperty()
}

class ModifiableNullableVFUEntity : ModifiableWorkspaceEntityBase<NullableVFUEntity>() {
  var data: String by EntityDataDelegation()
  var fileProperty: VirtualFileUrl? by VirtualFileUrlNullableProperty()
}

class ModifiableListVFUEntity : ModifiableWorkspaceEntityBase<ListVFUEntity>() {
  var data: String by EntityDataDelegation()
  var fileProperty: List<VirtualFileUrl> by VirtualFileUrlListProperty()
}

fun WorkspaceEntityStorageBuilder.addVFUEntity(data: String,
                                               fileUrl: String,
                                               virtualFileManager: VirtualFileUrlManager,
                                               source: EntitySource = SampleEntitySource("test")): VFUEntity {
  return addEntity(ModifiableVFUEntity::class.java, source) {
    this.data = data
    this.fileProperty = virtualFileManager.fromUrl(fileUrl)
  }
}

fun WorkspaceEntityStorageBuilder.addVFU2Entity(data: String,
                                                fileUrl: String,
                                                secondFileUrl: String,
                                                virtualFileManager: VirtualFileUrlManager,
                                                source: EntitySource = SampleEntitySource("test")): VFUWithTwoPropertiesEntity {
  return addEntity(ModifiableVFUWithTwoPropertiesEntity::class.java, source) {
    this.data = data
    this.fileProperty = virtualFileManager.fromUrl(fileUrl)
    this.secondFileProperty = virtualFileManager.fromUrl(secondFileUrl)
  }
}

fun WorkspaceEntityStorageBuilder.addNullableVFUEntity(data: String,
                                                       fileUrl: String?,
                                                       virtualFileManager: VirtualFileUrlManager,
                                                       source: EntitySource = SampleEntitySource("test")): NullableVFUEntity {
  return addEntity(ModifiableNullableVFUEntity::class.java, source) {
    this.data = data
    if (fileUrl != null) this.fileProperty = virtualFileManager.fromUrl(fileUrl)
  }
}

fun WorkspaceEntityStorageBuilder.addListVFUEntity(data: String,
                                                   fileUrl: List<String>,
                                                   virtualFileManager: VirtualFileUrlManager,
                                                   source: EntitySource = SampleEntitySource("test")): ListVFUEntity {
  return addEntity(ModifiableListVFUEntity::class.java, source) {
    this.data = data
    this.fileProperty = fileUrl.map { virtualFileManager.fromUrl(it) }
  }
}
