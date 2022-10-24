// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

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
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ArtifactExternalSystemIdEntityImpl(val dataSource: ArtifactExternalSystemIdEntityData) : ArtifactExternalSystemIdEntity, WorkspaceEntityBase() {

  companion object {
    internal val ARTIFACTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java,
                                                                                  ArtifactExternalSystemIdEntity::class.java,
                                                                                  ConnectionId.ConnectionType.ONE_TO_ONE, false)

    val connections = listOf<ConnectionId>(
      ARTIFACTENTITY_CONNECTION_ID,
    )

  }

  override val externalSystemId: String
    get() = dataSource.externalSystemId

  override val artifactEntity: ArtifactEntity
    get() = snapshot.extractOneToOneParent(ARTIFACTENTITY_CONNECTION_ID, this)!!

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(var result: ArtifactExternalSystemIdEntityData?) : ModifiableWorkspaceEntityBase<ArtifactExternalSystemIdEntity>(), ArtifactExternalSystemIdEntity.Builder {
    constructor() : this(ArtifactExternalSystemIdEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ArtifactExternalSystemIdEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.result = null

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
        error("Field ArtifactExternalSystemIdEntity#externalSystemId should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToOneParent<WorkspaceEntityBase>(ARTIFACTENTITY_CONNECTION_ID, this) == null) {
          error("Field ArtifactExternalSystemIdEntity#artifactEntity should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, ARTIFACTENTITY_CONNECTION_ID)] == null) {
          error("Field ArtifactExternalSystemIdEntity#artifactEntity should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ArtifactExternalSystemIdEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.externalSystemId != dataSource.externalSystemId) this.externalSystemId = dataSource.externalSystemId
      if (parents != null) {
        val artifactEntityNew = parents.filterIsInstance<ArtifactEntity>().single()
        if ((this.artifactEntity as WorkspaceEntityBase).id != (artifactEntityNew as WorkspaceEntityBase).id) {
          this.artifactEntity = artifactEntityNew
        }
      }
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override var externalSystemId: String
      get() = getEntityData().externalSystemId
      set(value) {
        checkModificationAllowed()
        getEntityData().externalSystemId = value
        changedProperty.add("externalSystemId")
      }

    override var artifactEntity: ArtifactEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneParent(ARTIFACTENTITY_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                         ARTIFACTENTITY_CONNECTION_ID)]!! as ArtifactEntity
        }
        else {
          this.entityLinks[EntityLink(false, ARTIFACTENTITY_CONNECTION_ID)]!! as ArtifactEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(true, ARTIFACTENTITY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(ARTIFACTENTITY_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(true, ARTIFACTENTITY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, ARTIFACTENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("artifactEntity")
      }

    override fun getEntityData(): ArtifactExternalSystemIdEntityData = result ?: super.getEntityData() as ArtifactExternalSystemIdEntityData
    override fun getEntityClass(): Class<ArtifactExternalSystemIdEntity> = ArtifactExternalSystemIdEntity::class.java
  }
}

class ArtifactExternalSystemIdEntityData : WorkspaceEntityData<ArtifactExternalSystemIdEntity>() {
  lateinit var externalSystemId: String

  fun isExternalSystemIdInitialized(): Boolean = ::externalSystemId.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ArtifactExternalSystemIdEntity> {
    val modifiable = ArtifactExternalSystemIdEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ArtifactExternalSystemIdEntity {
    return getCached(snapshot) {
      val entity = ArtifactExternalSystemIdEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ArtifactExternalSystemIdEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return ArtifactExternalSystemIdEntity(externalSystemId, entitySource) {
      this.artifactEntity = parents.filterIsInstance<ArtifactEntity>().single()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ArtifactEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ArtifactExternalSystemIdEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.externalSystemId != other.externalSystemId) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ArtifactExternalSystemIdEntityData

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
