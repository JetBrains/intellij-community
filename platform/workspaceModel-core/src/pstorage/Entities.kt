// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.ModifiableTypedEntity
import com.intellij.workspace.api.TypedEntity
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

internal interface PTypedEntity<E : TypedEntity> : TypedEntity {
  val id: PId<E>

  fun asStr(): String = "${javaClass.simpleName}-:-$id"
}

internal interface PModifiableTypedEntity<T : PTypedEntity<T>> : PTypedEntity<T>, ModifiableTypedEntity<T>

internal data class PId<E : TypedEntity>(val arrayId: Int, val clazz: KClass<E>) {
  init {
    if (arrayId < 0) error("ArrayId cannot be negative: $arrayId")
  }

  override fun toString(): String = arrayId.toString()
}

internal interface PEntityData<E : TypedEntity> {
  var entitySource: EntitySource
  var id: Int
  fun createEntity(snapshot: PEntityStorage): E
  fun wrapAsModifiable(diff: PEntityStorageBuilder): ModifiableTypedEntity<E>
  fun clone(): PEntityData<E>
}

internal class PFolderEntityData : PEntityData<PFolderEntity> {
  override var id: Int = -1
  override lateinit var entitySource: EntitySource
  lateinit var data: String

  override fun createEntity(snapshot: PEntityStorage) = PFolderEntity(entitySource, id, data, snapshot)

  override fun wrapAsModifiable(diff: PEntityStorageBuilder) = PFolderModifiableEntity(this, diff)

  override fun clone() = PFolderEntityData().also {
    it.id = this.id
    it.entitySource = this.entitySource
    it.data = this.data
  }
}

internal class PSoftSubFolderEntityData : PEntityData<PSoftSubFolder> {
  override var id: Int = -1
  override lateinit var entitySource: EntitySource

  override fun createEntity(snapshot: PEntityStorage) = PSoftSubFolder(entitySource, id, snapshot)

  override fun wrapAsModifiable(diff: PEntityStorageBuilder): ModifiableTypedEntity<PSoftSubFolder> {
    return PSoftSubFolderModifiableEntity(this, diff)
  }

  override fun clone() = PSoftSubFolderEntityData().also {
    it.id = this.id
    it.entitySource = this.entitySource
  }
}

internal class PSubFolderEntityData : PEntityData<PSubFolderEntity> {
  override var id: Int = -1
  override lateinit var entitySource: EntitySource
  lateinit var data: String

  override fun createEntity(snapshot: PEntityStorage) = PSubFolderEntity(entitySource, id, data, snapshot)

  override fun wrapAsModifiable(diff: PEntityStorageBuilder): PSubFolderModifiableEntity {
    return PSubFolderModifiableEntity(this, diff)
  }

  override fun clone() = PSubFolderEntityData().also {
    it.id = id
    it.entitySource = entitySource
    it.data = data
  }
}

internal class PFolderEntity(
  override val entitySource: EntitySource,
  arrayId: Int,
  val data: String,
  val snapshot: PEntityStorage
) : PTypedEntity<PFolderEntity> {

  override val id: PId<PFolderEntity> = PId(arrayId, this.javaClass.kotlin)

  val children: Sequence<PSubFolderEntity> by OneToMany.HardRef(snapshot, PSubFolderEntity::class)
  val softChildren: Sequence<PSoftSubFolder> by OneToMany.SoftRef(snapshot, PSoftSubFolder::class)

  override fun hasEqualProperties(e: TypedEntity): Boolean = TODO("Not yet implemented")

  override fun toString(): String = asStr()
}

internal class PSoftSubFolder(
  override val entitySource: EntitySource,
  arrayId: Int,
  val snapshot: PEntityStorage
) : PTypedEntity<PSoftSubFolder> {

  override val id: PId<PSoftSubFolder> = PId(arrayId, this.javaClass.kotlin)

  val parent: PFolderEntity? by ManyToOne.SoftRef(snapshot, PFolderEntity::class)

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    TODO("Not yet implemented")
  }

  override fun toString(): String = asStr()
}

internal class PSubFolderEntity(
  override val entitySource: EntitySource,
  arrayId: Int,
  val data: String,
  val snapshot: PEntityStorage
) : PTypedEntity<PSubFolderEntity> {

  override val id: PId<PSubFolderEntity> = PId(arrayId, this.javaClass.kotlin)

  val parent: PFolderEntity? by ManyToOne.HardRef(snapshot, PFolderEntity::class)

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    TODO("Not yet implemented")
  }

  override fun toString(): String = asStr()
}

@PEntityDataClass(PFolderEntityData::class)
@PEntityClass(PFolderEntity::class)
internal class PFolderModifiableEntity(val original: PFolderEntityData,
                              val diff: PEntityStorageBuilder) : PModifiableTypedEntity<PFolderEntity> {
  override fun hasEqualProperties(e: TypedEntity): Boolean = TODO("Not yet implemented")

  override var entitySource: EntitySource = original.entitySource

  var data: String by Another(original)

  var children: Sequence<PSubFolderEntity> by MutableOneToMany.HardRef(diff, PFolderEntity::class, PSubFolderEntity::class)
  var softChildren: Sequence<PSoftSubFolder> by MutableOneToMany.SoftRef(diff, PFolderEntity::class, PSoftSubFolder::class)

  override val id: PId<PFolderEntity> = PId(original.id, PFolderEntity::class)
}

@PEntityDataClass(PSubFolderEntityData::class)
@PEntityClass(PSubFolderEntity::class)
internal class PSubFolderModifiableEntity(val original: PSubFolderEntityData,
                                 val diff: PEntityStorageBuilder) : PModifiableTypedEntity<PSubFolderEntity> {
  override val id: PId<PSubFolderEntity> = PId(original.id, PSubFolderEntity::class)

  var data: String by Another(original)

  var parent: PFolderEntity? by MutableManyToOne.HardRef(diff, PSubFolderEntity::class, PFolderEntity::class)

  override val entitySource: EntitySource = original.entitySource

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    TODO("Not yet implemented")
  }
}

internal class Another<A, B>(val original: Any) : ReadWriteProperty<A, B> {
  override fun getValue(thisRef: A, property: KProperty<*>): B {
    return ((original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>).get(original) as B
  }

  override fun setValue(thisRef: A, property: KProperty<*>, value: B) {
    ((original::class.memberProperties.first { it.name == property.name }) as KMutableProperty<*>).setter.call(original, value)
  }
}

@PEntityDataClass(PSoftSubFolderEntityData::class)
@PEntityClass(PSoftSubFolder::class)
internal class PSoftSubFolderModifiableEntity(
  val original: PSoftSubFolderEntityData,
  val diff: PEntityStorageBuilder
) : PModifiableTypedEntity<PSoftSubFolder> {

  var parent: PFolderEntity? by MutableManyToOne.SoftRef(diff, PSoftSubFolder::class, PFolderEntity::class)

  override val id: PId<PSoftSubFolder> = PId(original.id, PSoftSubFolder::class)

  override val entitySource: EntitySource = original.entitySource

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    TODO("Not yet implemented")
  }
}

internal object MySource : EntitySource

internal annotation class PEntityDataClass(val clazz: KClass<out PEntityData<*>>)
internal annotation class PEntityClass(val clazz: KClass<out PTypedEntity<*>>)
