// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.MutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.MutableWorkspaceSet
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceSet
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class CollectionFieldEntityImpl : CollectionFieldEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  @JvmField
  var _versions: Set<Int>? = null
  override val versions: Set<Int>
    get() = _versions!!

  @JvmField
  var _names: List<String>? = null
  override val names: List<String>
    get() = _names!!

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: CollectionFieldEntityData?) : ModifiableWorkspaceEntityBase<CollectionFieldEntity>(), CollectionFieldEntity.Builder {
    constructor() : this(CollectionFieldEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity CollectionFieldEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isVersionsInitialized()) {
        error("Field CollectionFieldEntity#versions should be initialized")
      }
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field CollectionFieldEntity#entitySource should be initialized")
      }
      if (!getEntityData().isNamesInitialized()) {
        error("Field CollectionFieldEntity#names should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }


    private val versionsUpdater: (value: Set<Int>) -> Unit = { value ->

      changedProperty.add("versions")
    }
    override var versions: MutableSet<Int>
      get() {
        val collection_versions = getEntityData().versions
        if (collection_versions !is MutableWorkspaceSet) return collection_versions
        collection_versions.setModificationUpdateAction(versionsUpdater)
        return collection_versions
      }
      set(value) {
        checkModificationAllowed()
        getEntityData().versions = value
        versionsUpdater.invoke(value)
      }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    private val namesUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("names")
    }
    override var names: MutableList<String>
      get() {
        val collection_names = getEntityData().names
        if (collection_names !is MutableWorkspaceList) return collection_names
        collection_names.setModificationUpdateAction(namesUpdater)
        return collection_names
      }
      set(value) {
        checkModificationAllowed()
        getEntityData().names = value
        namesUpdater.invoke(value)
      }

    override fun getEntityData(): CollectionFieldEntityData = result ?: super.getEntityData() as CollectionFieldEntityData
    override fun getEntityClass(): Class<CollectionFieldEntity> = CollectionFieldEntity::class.java
  }
}

class CollectionFieldEntityData : WorkspaceEntityData<CollectionFieldEntity>() {
  lateinit var versions: MutableSet<Int>
  lateinit var names: MutableList<String>

  fun isVersionsInitialized(): Boolean = ::versions.isInitialized
  fun isNamesInitialized(): Boolean = ::names.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<CollectionFieldEntity> {
    val modifiable = CollectionFieldEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): CollectionFieldEntity {
    val entity = CollectionFieldEntityImpl()
    entity._versions = versions.toSet()
    entity._names = names.toList()
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
  }

  override fun clone(): CollectionFieldEntityData {
    val clonedEntity = super.clone()
    clonedEntity as CollectionFieldEntityData
    clonedEntity.versions = clonedEntity.versions.toMutableWorkspaceSet()
    clonedEntity.names = clonedEntity.names.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return CollectionFieldEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as CollectionFieldEntityData

    if (this.versions != other.versions) return false
    if (this.entitySource != other.entitySource) return false
    if (this.names != other.names) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as CollectionFieldEntityData

    if (this.versions != other.versions) return false
    if (this.names != other.names) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + versions.hashCode()
    result = 31 * result + names.hashCode()
    return result
  }
}
