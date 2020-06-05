// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.entities

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlListProperty
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlNullableProperty
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlProperty
import com.intellij.workspaceModel.storage.impl.references.ManyToOne
import com.intellij.workspaceModel.storage.impl.references.MutableManyToOne

internal data class SampleEntitySource(val name: String) : EntitySource

internal object MySource : EntitySource {
  override fun toString(): String = "MySource"
}

internal object AnotherSource : EntitySource {
  override fun toString(): String = "AnotherSource"
}

// ---------------------------------------

internal class SampleEntityData : WorkspaceEntityData<SampleEntity>() {
  var booleanProperty: Boolean = false
  lateinit var stringProperty: String
  lateinit var stringListProperty: List<String>
  lateinit var fileProperty: VirtualFileUrl

  override fun createEntity(snapshot: WorkspaceEntityStorage): SampleEntity {
    return SampleEntity(booleanProperty, stringProperty, stringListProperty, fileProperty).also { addMetaData(it, snapshot) }
  }
}

internal class SampleEntity(
  val booleanProperty: Boolean,
  val stringProperty: String,
  val stringListProperty: List<String>,
  val fileProperty: VirtualFileUrl
) : WorkspaceEntityBase()

internal class ModifiableSampleEntity : ModifiableWorkspaceEntityBase<SampleEntity>() {
  var booleanProperty: Boolean by EntityDataDelegation()
  var stringProperty: String by EntityDataDelegation()
  var stringListProperty: List<String> by EntityDataDelegation()
  var fileProperty: VirtualFileUrl by EntityDataDelegation()
}

internal fun WorkspaceEntityStorageDiffBuilder.addSampleEntity(stringProperty: String,
                                                               source: EntitySource = SampleEntitySource("test"),
                                                               booleanProperty: Boolean = false,
                                                               stringListProperty: MutableList<String> = ArrayList(),
                                                               virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManagerImpl(),
                                                               fileProperty: VirtualFileUrl = virtualFileManager.fromUrl("file:///tmp")): SampleEntity {
  return addEntity(ModifiableSampleEntity::class.java, source) {
    this.booleanProperty = booleanProperty
    this.stringProperty = stringProperty
    this.stringListProperty = stringListProperty
    this.fileProperty = fileProperty
  }
}

// ---------------------------------------

internal class SecondSampleEntityData : WorkspaceEntityData<SecondSampleEntity>() {
  var intProperty: Int = -1
  override fun createEntity(snapshot: WorkspaceEntityStorage): SecondSampleEntity {
    return SecondSampleEntity(intProperty).also { addMetaData(it, snapshot) }
  }
}

internal class SecondSampleEntity(
  val intProperty: Int
) : WorkspaceEntityBase()

internal class ModifiableSecondSampleEntity : ModifiableWorkspaceEntityBase<SecondSampleEntity>() {
  var intProperty: Int by EntityDataDelegation()
}

// ---------------------------------------

internal class SourceEntityData : WorkspaceEntityData<SourceEntity>() {
  lateinit var data: String
  override fun createEntity(snapshot: WorkspaceEntityStorage): SourceEntity {
    return SourceEntity(data).also { addMetaData(it, snapshot) }
  }
}

internal class SourceEntity(val data: String) : WorkspaceEntityBase()

internal class ModifiableSourceEntity : ModifiableWorkspaceEntityBase<SourceEntity>() {
  var data: String by EntityDataDelegation()
}

internal fun WorkspaceEntityStorageBuilder.addSourceEntity(data: String,
                                                           source: EntitySource): SourceEntity {
  return addEntity(ModifiableSourceEntity::class.java, source) {
    this.data = data
  }
}

// ---------------------------------------

internal class ChildSourceEntityData : WorkspaceEntityData<ChildSourceEntity>() {
  lateinit var data: String
  override fun createEntity(snapshot: WorkspaceEntityStorage): ChildSourceEntity {
    return ChildSourceEntity(data).also { addMetaData(it, snapshot) }
  }
}

internal class ChildSourceEntity(val data: String) : WorkspaceEntityBase() {
  val parent: SourceEntity by ManyToOne.NotNull(SourceEntity::class.java)
}

internal class ModifiableChildSourceEntity : ModifiableWorkspaceEntityBase<ChildSourceEntity>() {
  var data: String by EntityDataDelegation()
  var parent: SourceEntity by MutableManyToOne.NotNull(ChildSourceEntity::class.java, SourceEntity::class.java)
}

// ---------------------------------------

internal class ChildSampleEntityData : WorkspaceEntityData<ChildSampleEntity>() {
  lateinit var data: String
  override fun createEntity(snapshot: WorkspaceEntityStorage): ChildSampleEntity {
    return ChildSampleEntity(data).also { addMetaData(it, snapshot) }
  }
}

internal class ChildSampleEntity(
  val data: String
) : WorkspaceEntityBase() {
  val parent: SampleEntity? by ManyToOne.Nullable(SampleEntity::class.java)
}

internal class ModifiableChildSampleEntity : ModifiableWorkspaceEntityBase<ChildSampleEntity>() {
  var data: String by EntityDataDelegation()
  var parent: SampleEntity? by MutableManyToOne.Nullable(ChildSampleEntity::class.java, SampleEntity::class.java)
}

internal fun WorkspaceEntityStorageBuilder.addChildSampleEntity(stringProperty: String,
                                                                parent: SampleEntity?,
                                                                source: EntitySource = SampleEntitySource("test")): ChildSampleEntity {
  return addEntity(ModifiableChildSampleEntity::class.java, source) {
    this.data = stringProperty
    this.parent = parent
  }
}

internal class PersistentIdEntityData : WorkspaceEntityData.WithCalculablePersistentId<PersistentIdEntity>() {
  lateinit var data: String
  override fun createEntity(snapshot: WorkspaceEntityStorage): PersistentIdEntity {
    return PersistentIdEntity(data).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): LinkedListEntityId = LinkedListEntityId(data)
}

internal class PersistentIdEntity(val data: String) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  override fun persistentId(): LinkedListEntityId = LinkedListEntityId(data)
}

internal class ModifiablePersistentIdEntity : ModifiableWorkspaceEntityBase<PersistentIdEntity>() {
  var data: String by EntityDataDelegation()
}

internal fun WorkspaceEntityStorageBuilder.addPersistentIdEntity(data: String,
                                                                 source: EntitySource = SampleEntitySource("test")): PersistentIdEntity {
  return addEntity(ModifiablePersistentIdEntity::class.java, source) {
    this.data = data
  }
}

internal class VFUEntityData : WorkspaceEntityData<VFUEntity>() {
  lateinit var data: String
  lateinit var fileProperty: VirtualFileUrl
  override fun createEntity(snapshot: WorkspaceEntityStorage): VFUEntity {
    return VFUEntity(data, fileProperty).also { addMetaData(it, snapshot) }
  }
}

internal class VFUWithTwoPropertiesEntityData : WorkspaceEntityData<VFUWithTwoPropertiesEntity>() {
  lateinit var data: String
  lateinit var fileProperty: VirtualFileUrl
  lateinit var secondFileProperty: VirtualFileUrl
  override fun createEntity(snapshot: WorkspaceEntityStorage): VFUWithTwoPropertiesEntity {
    return VFUWithTwoPropertiesEntity(data, fileProperty, secondFileProperty).also { addMetaData(it, snapshot) }
  }
}

internal class NullableVFUEntityData : WorkspaceEntityData<NullableVFUEntity>() {
  lateinit var data: String
  var fileProperty: VirtualFileUrl? = null
  override fun createEntity(snapshot: WorkspaceEntityStorage): NullableVFUEntity {
    return NullableVFUEntity(data, fileProperty).also { addMetaData(it, snapshot) }
  }
}

internal class ListVFUEntityData : WorkspaceEntityData<ListVFUEntity>() {
  lateinit var data: String
  lateinit var fileProperty: List<VirtualFileUrl>
  override fun createEntity(snapshot: WorkspaceEntityStorage): ListVFUEntity {
    return ListVFUEntity(data, fileProperty).also { addMetaData(it, snapshot) }
  }
}

internal class VFUEntity(val data: String, val fileProperty: VirtualFileUrl) : WorkspaceEntityBase()
internal class VFUWithTwoPropertiesEntity(val data: String,
                                          val fileProperty: VirtualFileUrl,
                                          val secondFileProperty: VirtualFileUrl) : WorkspaceEntityBase()

internal class NullableVFUEntity(val data: String, val fileProperty: VirtualFileUrl?) : WorkspaceEntityBase()
internal class ListVFUEntity(val data: String, val fileProperty: List<VirtualFileUrl>) : WorkspaceEntityBase()

internal class ModifiableVFUEntity : ModifiableWorkspaceEntityBase<VFUEntity>() {
  var data: String by EntityDataDelegation()
  var fileProperty: VirtualFileUrl by VirtualFileUrlProperty()
}

internal class ModifiableVFUWithTwoPropertiesEntity : ModifiableWorkspaceEntityBase<VFUWithTwoPropertiesEntity>() {
  var data: String by EntityDataDelegation()
  var fileProperty: VirtualFileUrl by VirtualFileUrlProperty()
  var secondFileProperty: VirtualFileUrl by VirtualFileUrlProperty()
}

internal class ModifiableNullableVFUEntity : ModifiableWorkspaceEntityBase<NullableVFUEntity>() {
  var data: String by EntityDataDelegation()
  var fileProperty: VirtualFileUrl? by VirtualFileUrlNullableProperty()
}

internal class ModifiableListVFUEntity : ModifiableWorkspaceEntityBase<ListVFUEntity>() {
  var data: String by EntityDataDelegation()
  var fileProperty: List<VirtualFileUrl> by VirtualFileUrlListProperty()
}

internal fun WorkspaceEntityStorageBuilder.addVFUEntity(data: String,
                                                        fileUrl: String,
                                                        virtualFileManager: VirtualFileUrlManager,
                                                        source: EntitySource = SampleEntitySource("test")): VFUEntity {
  return addEntity(ModifiableVFUEntity::class.java, source) {
    this.data = data
    this.fileProperty = virtualFileManager.fromUrl(fileUrl)
  }
}

internal fun WorkspaceEntityStorageBuilder.addVFU2Entity(data: String,
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

internal fun WorkspaceEntityStorageBuilder.addNullableVFUEntity(data: String,
                                                                fileUrl: String?,
                                                                virtualFileManager: VirtualFileUrlManager,
                                                                source: EntitySource = SampleEntitySource("test")): NullableVFUEntity {
  return addEntity(ModifiableNullableVFUEntity::class.java, source) {
    this.data = data
    if (fileUrl != null) this.fileProperty = virtualFileManager.fromUrl(fileUrl)
  }
}

internal fun WorkspaceEntityStorageBuilder.addListVFUEntity(data: String,
                                                            fileUrl: List<String>,
                                                            virtualFileManager: VirtualFileUrlManager,
                                                            source: EntitySource = SampleEntitySource("test")): ListVFUEntity {
  return addEntity(ModifiableListVFUEntity::class.java, source) {
    this.data = data
    this.fileProperty = fileUrl.map { virtualFileManager.fromUrl(it) }
  }
}
