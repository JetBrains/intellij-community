// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.*
import com.intellij.workspace.api.pstorage.indices.VirtualFileUrlListProperty
import com.intellij.workspace.api.pstorage.indices.VirtualFileUrlNullableProperty
import com.intellij.workspace.api.pstorage.indices.VirtualFileUrlProperty
import com.intellij.workspace.api.pstorage.references.ManyToOne
import com.intellij.workspace.api.pstorage.references.MutableManyToOne
import com.intellij.workspace.api.pstorage.references.OneToMany

internal data class PSampleEntitySource(val name: String) : EntitySource

internal object AnotherSource : EntitySource

internal object MySource : EntitySource

// ---------------------------------------

internal class PSampleEntityData : PEntityData<PSampleEntity>() {
  var booleanProperty: Boolean = false
  lateinit var stringProperty: String
  lateinit var stringListProperty: List<String>
  lateinit var fileProperty: VirtualFileUrl

  override fun createEntity(snapshot: TypedEntityStorage): PSampleEntity {
    return PSampleEntity(booleanProperty, stringProperty, stringListProperty, fileProperty).also { addMetaData(it, snapshot) }
  }
}

internal class PSampleEntity(
  val booleanProperty: Boolean,
  val stringProperty: String,
  val stringListProperty: List<String>,
  val fileProperty: VirtualFileUrl
) : PTypedEntity()

internal class ModifiablePSampleEntity : PModifiableTypedEntity<PSampleEntity>() {
  var booleanProperty: Boolean by EntityDataDelegation()
  var stringProperty: String by EntityDataDelegation()
  var stringListProperty: List<String> by EntityDataDelegation()
  var fileProperty: VirtualFileUrl by EntityDataDelegation()

}

internal fun TypedEntityStorageBuilder.addPSampleEntity(stringProperty: String,
                                                        source: EntitySource = PSampleEntitySource("test"),
                                                        booleanProperty: Boolean = false,
                                                        stringListProperty: MutableList<String> = ArrayList(),
                                                        virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManagerImpl(),
                                                        fileProperty: VirtualFileUrl = virtualFileManager.fromUrl(
                                                          "file:///tmp")): PSampleEntity {
  return addEntity(ModifiablePSampleEntity::class.java, source) {
    this.booleanProperty = booleanProperty
    this.stringProperty = stringProperty
    this.stringListProperty = stringListProperty
    this.fileProperty = fileProperty
  }
}

// ---------------------------------------

internal class SecondSampleEntityData : PEntityData<SecondSampleEntity>() {
  var intProperty: Int = -1
  override fun createEntity(snapshot: TypedEntityStorage): SecondSampleEntity {
    return SecondSampleEntity(intProperty).also { addMetaData(it, snapshot) }
  }
}

internal class SecondSampleEntity(
  val intProperty: Int
) : PTypedEntity()

internal class ModifiableSecondSampleEntity : PModifiableTypedEntity<SecondSampleEntity>() {
  var intProperty: Int by EntityDataDelegation()
}

// ---------------------------------------

internal class PSourceEntityData : PEntityData<PSourceEntity>() {
  lateinit var data: String
  override fun createEntity(snapshot: TypedEntityStorage): PSourceEntity {
    return PSourceEntity(data).also { addMetaData(it, snapshot) }
  }
}

internal class PSourceEntity(val data: String) : PTypedEntity()

internal class ModifiablePSourceEntity : PModifiableTypedEntity<PSourceEntity>() {
  var data: String by EntityDataDelegation()
}

internal fun TypedEntityStorageBuilder.addPSourceEntity(data: String,
                                                        source: EntitySource): PSourceEntity {
  return addEntity(ModifiablePSourceEntity::class.java, source) {
    this.data = data
  }
}

// ---------------------------------------

internal class PChildSourceEntityData : PEntityData<PChildSourceEntity>() {
  lateinit var data: String
  override fun createEntity(snapshot: TypedEntityStorage): PChildSourceEntity {
    return PChildSourceEntity(data).also { addMetaData(it, snapshot) }
  }
}

internal class PChildSourceEntity(val data: String) : PTypedEntity() {
  val parent: PSourceEntity by ManyToOne.NotNull(PSourceEntity::class.java)
}

internal class ModifiablePChildSourceEntity : PModifiableTypedEntity<PChildSourceEntity>() {
  var data: String by EntityDataDelegation()
  var parent: PSourceEntity by MutableManyToOne.NotNull(PChildSourceEntity::class.java, PSourceEntity::class.java)
}

// ---------------------------------------

internal class PNamedSampleEntityData : PEntityData.WithCalculatablePersistentId<PNamedSampleEntity>() {
  lateinit var name: String
  lateinit var next: PSampleEntityId
  override fun createEntity(snapshot: TypedEntityStorage): PNamedSampleEntity {
    return PNamedSampleEntity(name, next).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): PSampleEntityId = PSampleEntityId(name)
}

internal class PNamedSampleEntity(
  val name: String,
  val next: PSampleEntityId
) : TypedEntityWithPersistentId, PTypedEntity() {

  override fun persistentId(): PSampleEntityId = PSampleEntityId(name)
}

internal data class PSampleEntityId(val name: String) : PersistentEntityId<PNamedSampleEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

internal class ModifiablePNamedSampleEntity : PModifiableTypedEntity<PNamedSampleEntity>() {
  var name: String by EntityDataDelegation()
  var next: PSampleEntityId by EntityDataDelegation()
}

internal data class PChildEntityId(val childName: String,
                                   override val parentId: PSampleEntityId) : PersistentEntityId<PChildWithPersistentIdEntity>() {
  override val presentableName: String
    get() = childName
}

// ---------------------------------------

internal class PChildWithPersistentIdEntityData : PEntityData.WithPersistentId<PChildWithPersistentIdEntity>() {
  lateinit var parent: PNamedSampleEntity
  lateinit var childName: String
  override fun createEntity(snapshot: TypedEntityStorage): PChildWithPersistentIdEntity {
    return PChildWithPersistentIdEntity(parent, childName).also { addMetaData(it, snapshot) }
  }
}

internal class PChildWithPersistentIdEntity(
  val parent: PNamedSampleEntity,
  val childName: String
) : PTypedEntity(), TypedEntityWithPersistentId {
  override fun persistentId(): PersistentEntityId<*> = PChildEntityId(childName, parent.persistentId())
}

internal class ModifiablePChildWithPersistentIdEntity : PModifiableTypedEntity<PChildWithPersistentIdEntity>() {
  var parent: PNamedSampleEntity by EntityDataDelegation()
  var childName: String by EntityDataDelegation()
}

internal fun PEntityStorageBuilder.addPNamedEntity(name: String, next: PSampleEntityId) =
  addEntity(ModifiablePNamedSampleEntity::class.java, PSampleEntitySource("test")) {
    this.name = name
    this.next = next
  }

internal class PChildSampleEntityData : PEntityData<PChildSampleEntity>() {
  lateinit var data: String
  override fun createEntity(snapshot: TypedEntityStorage): PChildSampleEntity {
    return PChildSampleEntity(data).also { addMetaData(it, snapshot) }
  }
}

internal class PChildSampleEntity(
  val data: String
) : PTypedEntity() {
  val parent: PSampleEntity? by ManyToOne.Nullable(PSampleEntity::class.java)
}

internal class ModifiablePChildSampleEntity : PModifiableTypedEntity<PChildSampleEntity>() {
  var data: String by EntityDataDelegation()
  var parent: PSampleEntity? by MutableManyToOne.Nullable(PChildSampleEntity::class.java, PSampleEntity::class.java)
}

internal fun TypedEntityStorageBuilder.addPChildSampleEntity(stringProperty: String,
                                                             parent: PSampleEntity?,
                                                             source: EntitySource = PSampleEntitySource("test")): PChildSampleEntity {
  return addEntity(ModifiablePChildSampleEntity::class.java, source) {
    this.data = stringProperty
    this.parent = parent
  }
}

internal class PPersistentIdEntityData : PEntityData.WithCalculatablePersistentId<PPersistentIdEntity>() {
  lateinit var data: String
  override fun createEntity(snapshot: TypedEntityStorage): PPersistentIdEntity {
    return PPersistentIdEntity(data).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): PSampleEntityId = PSampleEntityId(data)
}

internal class PPersistentIdEntity(val data: String) : TypedEntityWithPersistentId, PTypedEntity() {
  override fun persistentId(): PSampleEntityId = PSampleEntityId(data)
}

internal class ModifiablePPersistentIdEntity : PModifiableTypedEntity<PPersistentIdEntity>() {
  var data: String by EntityDataDelegation()
}

internal fun TypedEntityStorageBuilder.addPersistentIdEntity(data: String,
                                                             source: EntitySource = PSampleEntitySource("test")): PPersistentIdEntity {
  return addEntity(ModifiablePPersistentIdEntity::class.java, source) {
    this.data = data
  }
}

internal class PChildEntityData : PEntityData<PChildEntity>() {
  lateinit var childProperty: String
  var dataClass: PDataClass? = null
  override fun createEntity(snapshot: TypedEntityStorage): PChildEntity {
    return PChildEntity(childProperty, dataClass).also { addMetaData(it, snapshot) }
  }
}

internal class PChildEntity(
  val childProperty: String,
  val dataClass: PDataClass?
) : PTypedEntity() {
  val parent: PParentEntity by ManyToOne.NotNull(PParentEntity::class.java)
}

internal class PNoDataChildEntityData : PEntityData<PNoDataChildEntity>() {
  lateinit var childProperty: String
  override fun createEntity(snapshot: TypedEntityStorage): PNoDataChildEntity {
    return PNoDataChildEntity(childProperty).also { addMetaData(it, snapshot) }
  }
}

internal class PNoDataChildEntity(
  val childProperty: String
) : PTypedEntity() {
  val parent: PParentEntity by ManyToOne.NotNull(PParentEntity::class.java)
}

internal class PChildChildEntityData : PEntityData<PChildChildEntity>() {
  override fun createEntity(snapshot: TypedEntityStorage): PChildChildEntity {
    return PChildChildEntity().also { addMetaData(it, snapshot) }
  }
}

internal class PChildChildEntity : PTypedEntity() {
  val parent1: PParentEntity by ManyToOne.NotNull(PParentEntity::class.java)
  val parent2: PChildEntity by ManyToOne.NotNull(PChildEntity::class.java)
}

internal class PParentEntityData : PEntityData<PParentEntity>() {
  lateinit var parentProperty: String
  override fun createEntity(snapshot: TypedEntityStorage): PParentEntity {
    return PParentEntity(parentProperty).also { addMetaData(it, snapshot) }
  }
}

internal class PParentEntity(
  val parentProperty: String
) : PTypedEntity() {

  val children: Sequence<PChildEntity> by OneToMany(PChildEntity::class.java, false)

  val noDataChildren: Sequence<PNoDataChildEntity> by OneToMany(PNoDataChildEntity::class.java, false)

  val optionalChildren: Sequence<PChildWithOptionalParentEntity> by OneToMany(PChildWithOptionalParentEntity::class.java, true)
}

internal data class PDataClass(val stringProperty: String, val parent: EntityReference<PParentEntity>)

internal class PChildWithOptionalParentEntityData : PEntityData<PChildWithOptionalParentEntity>() {
  lateinit var childProperty: String
  override fun createEntity(snapshot: TypedEntityStorage): PChildWithOptionalParentEntity {
    return PChildWithOptionalParentEntity(childProperty).also { addMetaData(it, snapshot) }
  }
}

internal class PChildWithOptionalParentEntity(
  val childProperty: String
) : PTypedEntity() {
  val optionalParent: PParentEntity? by ManyToOne.Nullable(PParentEntity::class.java)
}

internal class ModifiablePChildWithOptionalParentEntity : PModifiableTypedEntity<PChildWithOptionalParentEntity>() {
  var optionalParent: PParentEntity? by MutableManyToOne.Nullable(PChildWithOptionalParentEntity::class.java, PParentEntity::class.java)
  var childProperty: String by EntityDataDelegation()
}

internal class ModifiablePChildEntity : PModifiableTypedEntity<PChildEntity>() {
  var childProperty: String by EntityDataDelegation()
  var dataClass: PDataClass? by EntityDataDelegation()
  var parent: PParentEntity by MutableManyToOne.NotNull(PChildEntity::class.java, PParentEntity::class.java)
}

internal class ModifiablePNoDataChildEntity : PModifiableTypedEntity<PNoDataChildEntity>() {
  var childProperty: String by EntityDataDelegation()
  var parent: PParentEntity by MutableManyToOne.NotNull(PNoDataChildEntity::class.java, PParentEntity::class.java)
}

internal class ModifiablePChildChildEntity : PModifiableTypedEntity<PChildChildEntity>() {
  var parent1: PParentEntity by MutableManyToOne.NotNull(PChildChildEntity::class.java, PParentEntity::class.java)
  var parent2: PChildEntity by MutableManyToOne.NotNull(PChildChildEntity::class.java, PChildEntity::class.java)
}

internal class ModifiablePParentEntity : PModifiableTypedEntity<PParentEntity>() {
  var parentProperty: String by EntityDataDelegation()
}

internal fun TypedEntityStorageBuilder.addPParentEntity(parentProperty: String = "parent",
                                                        source: EntitySource = PSampleEntitySource("test")) =
  addEntity(ModifiablePParentEntity::class.java, source) {
    this.parentProperty = parentProperty
  }

internal fun TypedEntityStorageBuilder.addPChildWithOptionalParentEntity(parentEntity: PParentEntity?,
                                                                         childProperty: String = "child",
                                                                         source: PSampleEntitySource = PSampleEntitySource("test")) =
  addEntity(ModifiablePChildWithOptionalParentEntity::class.java, source) {
    this.optionalParent = parentEntity
    this.childProperty = childProperty
  }

internal fun TypedEntityStorageBuilder.addPChildEntity(parentEntity: PParentEntity = addPParentEntity(),
                                                       childProperty: String = "child",
                                                       dataClass: PDataClass? = null,
                                                       source: EntitySource = PSampleEntitySource("test")) =
  addEntity(ModifiablePChildEntity::class.java, source) {
    this.parent = parentEntity
    this.childProperty = childProperty
    this.dataClass = dataClass
  }

internal fun TypedEntityStorageBuilder.addPNoDataChildEntity(parentEntity: PParentEntity = addPParentEntity(),
                                                             childProperty: String = "child",
                                                             source: PSampleEntitySource = PSampleEntitySource("test")) =
  addEntity(ModifiablePNoDataChildEntity::class.java, source) {
    this.parent = parentEntity
    this.childProperty = childProperty
  }

internal fun TypedEntityStorageBuilder.addPChildChildEntity(parent1: PParentEntity, parent2: PChildEntity) =
  addEntity(ModifiablePChildChildEntity::class.java, PSampleEntitySource("test")) {
    this.parent1 = parent1
    this.parent2 = parent2
  }


internal data class NameId(private val name: String) : PersistentEntityId<NamedEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name

  override fun toString(): String = name
}

@Suppress("unused")
internal class NamedEntityData : PEntityData.WithCalculatablePersistentId<NamedEntity>() {
  lateinit var name: String
  override fun createEntity(snapshot: TypedEntityStorage): NamedEntity {
    return NamedEntity(name).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): PersistentEntityId<*> = NameId(name)
}

internal class NamedEntity(
  val name: String
) : PTypedEntity(), TypedEntityWithPersistentId {
  override fun persistentId(): PersistentEntityId<*> = NameId(name)
}

internal class ModifiableNamedEntity : PModifiableTypedEntity<NamedEntity>() {
  var name: String by EntityDataDelegation()
}

internal class WithSoftLinkEntityData : PEntityData<WithSoftLinkEntity>(), PSoftLinkable {

  lateinit var link: NameId

  override fun createEntity(snapshot: TypedEntityStorage): WithSoftLinkEntity {
    return WithSoftLinkEntity(link).also { addMetaData(it, snapshot) }
  }

  override fun getLinks(): List<PersistentEntityId<*>> = listOf(link)

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>,
                          affectedIds: MutableList<Pair<PersistentEntityId<*>, PersistentEntityId<*>>>): Boolean {
    this.link = newLink as NameId
    return true
  }
}

internal class WithSoftLinkEntity(val link: NameId) : PTypedEntity()

internal class ModifiableWithSoftLinkEntity : PModifiableTypedEntity<WithSoftLinkEntity>() {
  var link: NameId by EntityDataDelegation()
}

internal class PVFUEntityData : PEntityData<PVFUEntity>() {
  lateinit var data: String
  lateinit var fileProperty: VirtualFileUrl
  override fun createEntity(snapshot: TypedEntityStorage): PVFUEntity {
    return PVFUEntity(data, fileProperty).also { addMetaData(it, snapshot) }
  }
}

internal class PVFUWithTwoPropertiesEntityData : PEntityData<PVFUWithTwoPropertiesEntity>() {
  lateinit var data: String
  lateinit var fileProperty: VirtualFileUrl
  lateinit var secondFileProperty: VirtualFileUrl
  override fun createEntity(snapshot: TypedEntityStorage): PVFUWithTwoPropertiesEntity {
    return PVFUWithTwoPropertiesEntity(data, fileProperty, secondFileProperty).also { addMetaData(it, snapshot) }
  }
}

internal class PNullableVFUEntityData : PEntityData<PNullableVFUEntity>() {
  lateinit var data: String
  var fileProperty: VirtualFileUrl? = null
  override fun createEntity(snapshot: TypedEntityStorage): PNullableVFUEntity {
    return PNullableVFUEntity(data, fileProperty).also { addMetaData(it, snapshot) }
  }
}

internal class PListVFUEntityData : PEntityData<PListVFUEntity>() {
  lateinit var data: String
  lateinit var fileProperty: List<VirtualFileUrl>
  override fun createEntity(snapshot: TypedEntityStorage): PListVFUEntity {
    return PListVFUEntity(data, fileProperty).also { addMetaData(it, snapshot) }
  }
}

internal class PVFUEntity(val data: String, val fileProperty: VirtualFileUrl) : PTypedEntity()
internal class PVFUWithTwoPropertiesEntity(val data: String,
                                           val fileProperty: VirtualFileUrl,
                                           val secondFileProperty: VirtualFileUrl) : PTypedEntity()

internal class PNullableVFUEntity(val data: String, val fileProperty: VirtualFileUrl?) : PTypedEntity()
internal class PListVFUEntity(val data: String, val fileProperty: List<VirtualFileUrl>) : PTypedEntity()

internal class ModifiablePVFUEntity : PModifiableTypedEntity<PVFUEntity>() {
  var data: String by EntityDataDelegation()
  var fileProperty: VirtualFileUrl by VirtualFileUrlProperty()
}

internal class ModifiablePVFUWithTwoPropertiesEntity : PModifiableTypedEntity<PVFUWithTwoPropertiesEntity>() {
  var data: String by EntityDataDelegation()
  var fileProperty: VirtualFileUrl by VirtualFileUrlProperty()
  var secondFileProperty: VirtualFileUrl by VirtualFileUrlProperty()
}

internal class ModifiablePNullableVFUEntity : PModifiableTypedEntity<PNullableVFUEntity>() {
  var data: String by EntityDataDelegation()
  var fileProperty: VirtualFileUrl? by VirtualFileUrlNullableProperty()
}

internal class ModifiablePListVFUEntity : PModifiableTypedEntity<PListVFUEntity>() {
  var data: String by EntityDataDelegation()
  var fileProperty: List<VirtualFileUrl> by VirtualFileUrlListProperty()
}

internal fun TypedEntityStorageBuilder.addPVFUEntity(data: String,
                                                     fileUrl: String,
                                                     virtualFileManager: VirtualFileUrlManager,
                                                     source: EntitySource = PSampleEntitySource("test")): PVFUEntity {
  return addEntity(ModifiablePVFUEntity::class.java, source) {
    this.data = data
    this.fileProperty = virtualFileManager.fromUrl(fileUrl)
  }
}

internal fun TypedEntityStorageBuilder.addPVFU2Entity(data: String,
                                                      fileUrl: String,
                                                      secondFileUrl: String,
                                                      virtualFileManager: VirtualFileUrlManager,
                                                      source: EntitySource = PSampleEntitySource("test")): PVFUWithTwoPropertiesEntity {
  return addEntity(ModifiablePVFUWithTwoPropertiesEntity::class.java, source) {
    this.data = data
    this.fileProperty = virtualFileManager.fromUrl(fileUrl)
    this.secondFileProperty = virtualFileManager.fromUrl(secondFileUrl)
  }
}

internal fun TypedEntityStorageBuilder.addPNullableVFUEntity(data: String,
                                                             fileUrl: String?,
                                                             virtualFileManager: VirtualFileUrlManager,
                                                             source: EntitySource = PSampleEntitySource("test")): PNullableVFUEntity {
  return addEntity(ModifiablePNullableVFUEntity::class.java, source) {
    this.data = data
    if (fileUrl != null) this.fileProperty = virtualFileManager.fromUrl(fileUrl)
  }
}

internal fun TypedEntityStorageBuilder.addPListVFUEntity(data: String,
                                                         fileUrl: List<String>,
                                                         virtualFileManager: VirtualFileUrlManager,
                                                         source: EntitySource = PSampleEntitySource("test")): PListVFUEntity {
  return addEntity(ModifiablePListVFUEntity::class.java, source) {
    this.data = data
    this.fileProperty = fileUrl.map { virtualFileManager.fromUrl(it) }
  }
}


internal interface NamedSampleEntity : TypedEntityWithPersistentId {
  val name: String
  val next: SampleEntityId

  @JvmDefault
  override fun persistentId(): SampleEntityId = SampleEntityId(name)
}

internal data class SampleEntityId(val name: String) : PersistentEntityId<NamedSampleEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

internal interface ModifiableNamedSampleEntity : ModifiableTypedEntity<NamedSampleEntity>, NamedSampleEntity {
  override var name: String
  override var next: SampleEntityId
}

internal data class ChildEntityId(val childName: String, override val parentId: SampleEntityId) : PersistentEntityId<ModifiableChildEntityWithPersistentId>() {
  override val presentableName: String
    get() = childName
}

internal interface ModifiableChildEntityWithPersistentId : ModifiableTypedEntity<ModifiableChildEntityWithPersistentId>, TypedEntityWithPersistentId {
  var parent: NamedSampleEntity
  var childName: String

  @JvmDefault
  override fun persistentId(): PersistentEntityId<*> = ChildEntityId(childName, parent.persistentId())
}

internal fun TypedEntityStorageBuilder.addNamedEntity(name: String, next: SampleEntityId) =
  addEntity(ModifiableNamedSampleEntity::class.java, PSampleEntitySource("test")) {
    this.name = name
    this.next = next
  }
