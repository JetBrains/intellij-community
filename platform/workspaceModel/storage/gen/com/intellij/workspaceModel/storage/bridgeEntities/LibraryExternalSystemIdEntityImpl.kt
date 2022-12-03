// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.openapi.util.NlsSafe
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import org.jetbrains.annotations.NonNls
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class LibraryExternalSystemIdEntityImpl(val dataSource: LibraryExternalSystemIdEntityData) : LibraryExternalSystemIdEntity, WorkspaceEntityBase() {

  companion object {
    internal val LIBRARY_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java,
                                                                           LibraryExternalSystemIdEntity::class.java,
                                                                           ConnectionId.ConnectionType.ONE_TO_ONE, false)

    val connections = listOf<ConnectionId>(
      LIBRARY_CONNECTION_ID,
    )

  }

  override val externalSystemId: String
    get() = dataSource.externalSystemId

  override val library: LibraryEntity
    get() = snapshot.extractOneToOneParent(LIBRARY_CONNECTION_ID, this)!!

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: LibraryExternalSystemIdEntityData?) : ModifiableWorkspaceEntityBase<LibraryExternalSystemIdEntity, LibraryExternalSystemIdEntityData>(
    result), LibraryExternalSystemIdEntity.Builder {
    constructor() : this(LibraryExternalSystemIdEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity LibraryExternalSystemIdEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isExternalSystemIdInitialized()) {
        error("Field LibraryExternalSystemIdEntity#externalSystemId should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToOneParent<WorkspaceEntityBase>(LIBRARY_CONNECTION_ID, this) == null) {
          error("Field LibraryExternalSystemIdEntity#library should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)] == null) {
          error("Field LibraryExternalSystemIdEntity#library should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as LibraryExternalSystemIdEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.externalSystemId != dataSource.externalSystemId) this.externalSystemId = dataSource.externalSystemId
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var externalSystemId: String
      get() = getEntityData().externalSystemId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).externalSystemId = value
        changedProperty.add("externalSystemId")
      }

    override var library: LibraryEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneParent(LIBRARY_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                  LIBRARY_CONNECTION_ID)]!! as LibraryEntity
        }
        else {
          this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)]!! as LibraryEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, LIBRARY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(LIBRARY_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, LIBRARY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)] = value
        }
        changedProperty.add("library")
      }

    override fun getEntityClass(): Class<LibraryExternalSystemIdEntity> = LibraryExternalSystemIdEntity::class.java
  }
}

class LibraryExternalSystemIdEntityData : WorkspaceEntityData<LibraryExternalSystemIdEntity>() {
  lateinit var externalSystemId: String

  fun isExternalSystemIdInitialized(): Boolean = ::externalSystemId.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<LibraryExternalSystemIdEntity> {
    val modifiable = LibraryExternalSystemIdEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): LibraryExternalSystemIdEntity {
    return getCached(snapshot) {
      val entity = LibraryExternalSystemIdEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return LibraryExternalSystemIdEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return LibraryExternalSystemIdEntity(externalSystemId, entitySource) {
      this.library = parents.filterIsInstance<LibraryEntity>().single()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(LibraryEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as LibraryExternalSystemIdEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.externalSystemId != other.externalSystemId) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as LibraryExternalSystemIdEntityData

    if (this.externalSystemId != other.externalSystemId) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + externalSystemId.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + externalSystemId.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
