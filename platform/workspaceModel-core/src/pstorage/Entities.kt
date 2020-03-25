// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.ModifiableTypedEntity
import com.intellij.workspace.api.TypedEntity
import kotlin.reflect.KClass

interface PTypedEntity<E : TypedEntity> : TypedEntity {
  val id: PId<E>

  fun asStr(): String = "${javaClass.simpleName}@$id"
}

interface PModifiableTypedEntity<T : PTypedEntity<T>> : PTypedEntity<T>, ModifiableTypedEntity<T>

data class PId<E : TypedEntity>(val arrayId: Int, val clazz: KClass<E>) {
  init {
    if (arrayId < 0) error("")
  }
}

interface PEntityData<E : TypedEntity> {
  var entitySource: EntitySource
  var id: Int
  fun createEntity(snapshot: PEntityStorage): E
  fun wrapAsModifiable(diff: PEntityStorageBuilder): ModifiableTypedEntity<E>
  fun clone(): PEntityData<E>
}

class PFolderEntityData : PEntityData<PFolderEntity> {
  override var id: Int = -1
  override lateinit var entitySource: EntitySource
  lateinit var data: String

  override fun createEntity(snapshot: PEntityStorage) = PFolderEntity(entitySource, PId(id, PFolderEntity::class), data, snapshot)

  override fun wrapAsModifiable(diff: PEntityStorageBuilder) = PFolderModifiableEntity(
    this, diff)

  override fun clone() = PFolderEntityData().also {
    it.id = this.id
    it.entitySource = this.entitySource
    it.data = this.data
  }
}

class PSubFolderEntityData : PEntityData<PSubFolderEntity> {
  override var id: Int = -1
  override lateinit var entitySource: EntitySource
  lateinit var data: String

  override fun createEntity(snapshot: PEntityStorage): PSubFolderEntity {
    return PSubFolderEntity(entitySource, PId(id, PSubFolderEntity::class), data, snapshot)
  }

  override fun wrapAsModifiable(diff: PEntityStorageBuilder): PSubFolderModifiableEntity {
    return PSubFolderModifiableEntity(this, diff)
  }

  override fun clone() = PSubFolderEntityData().also {
    it.id = id
    it.entitySource = entitySource
    it.data = data
  }
}

class PFolderEntity(
  override val entitySource: EntitySource,
  override val id: PId<PFolderEntity>,
  val data: String,
  val snapshot: PEntityStorage
) : PTypedEntity<PFolderEntity> {

  val children: Sequence<PSubFolderEntity> by HardRef(snapshot, PSubFolderEntity::parent)

  override fun hasEqualProperties(e: TypedEntity): Boolean = TODO("Not yet implemented")

  override fun toString(): String = asStr()
}

class PSubFolderEntity(
  override val entitySource: EntitySource,
  override val id: PId<PSubFolderEntity>,
  val data: String,
  val snapshot: PEntityStorage
) : PTypedEntity<PSubFolderEntity> {

  val parent: PFolderEntity? by HardBackRef(snapshot, PFolderEntity::children)

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    TODO("Not yet implemented")
  }

  override fun toString(): String = asStr()
}

@PEntityDataClass(PFolderEntityData::class)
class PFolderModifiableEntity(val original: PFolderEntityData,
                              val diff: PEntityStorageBuilder) : PModifiableTypedEntity<PFolderEntity> {
  override fun hasEqualProperties(e: TypedEntity): Boolean = TODO("Not yet implemented")

  override var entitySource: EntitySource = original.entitySource

  var data: String
    get() = original.data
    set(value) {
      original.data = value
    }

  var children: Sequence<PSubFolderEntity> by MutableHardRef(diff, PFolderEntity::children, PSubFolderEntity::parent)

  override val id: PId<PFolderEntity> = PId(
    original.id, PFolderEntity::class)
}

@PEntityDataClass(PSubFolderEntityData::class)
class PSubFolderModifiableEntity(val original: PSubFolderEntityData,
                                 val diff: PEntityStorageBuilder) : PModifiableTypedEntity<PSubFolderEntity> {
  override val id: PId<PSubFolderEntity> = PId(
    original.id, PSubFolderEntity::class)

  var data: String
    get() = original.data
    set(value) {
      original.data = value
    }

  var parent: PFolderEntity? by MutableHardBackRef(diff, PSubFolderEntity::parent, PFolderEntity::children)

  override val entitySource: EntitySource = original.entitySource

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    TODO("Not yet implemented")
  }
}

object MySource : EntitySource

annotation class PEntityDataClass(val clazz: KClass<out PEntityData<*>>)