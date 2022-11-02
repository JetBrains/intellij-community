// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.SymbolicEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.SoftLinkable
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyParent
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyParentOfChild
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ArtifactOutputPackagingElementEntityImpl(val dataSource: ArtifactOutputPackagingElementEntityData) : ArtifactOutputPackagingElementEntity, WorkspaceEntityBase() {

  companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java,
                                                                                PackagingElementEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, true)

    val connections = listOf<ConnectionId>(
      PARENTENTITY_CONNECTION_ID,
    )

  }

  override val parentEntity: CompositePackagingElementEntity?
    get() = snapshot.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this)

  override val artifact: ArtifactId?
    get() = dataSource.artifact

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(var result: ArtifactOutputPackagingElementEntityData?) : ModifiableWorkspaceEntityBase<ArtifactOutputPackagingElementEntity>(), ArtifactOutputPackagingElementEntity.Builder {
    constructor() : this(ArtifactOutputPackagingElementEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ArtifactOutputPackagingElementEntity is already created in a different builder")
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
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ArtifactOutputPackagingElementEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.artifact != dataSource?.artifact) this.artifact = dataSource.artifact
      if (parents != null) {
        val parentEntityNew = parents.filterIsInstance<CompositePackagingElementEntity?>().singleOrNull()
        if ((parentEntityNew == null && this.parentEntity != null) || (parentEntityNew != null && this.parentEntity == null) || (parentEntityNew != null && this.parentEntity != null && (this.parentEntity as WorkspaceEntityBase).id != (parentEntityNew as WorkspaceEntityBase).id)) {
          this.parentEntity = parentEntityNew
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

    override var parentEntity: CompositePackagingElementEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                                PARENTENTITY_CONNECTION_ID)] as? CompositePackagingElementEntity
        }
        else {
          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? CompositePackagingElementEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToAbstractManyParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("parentEntity")
      }

    override var artifact: ArtifactId?
      get() = getEntityData().artifact
      set(value) {
        checkModificationAllowed()
        getEntityData().artifact = value
        changedProperty.add("artifact")

      }

    override fun getEntityData(): ArtifactOutputPackagingElementEntityData = result
                                                                             ?: super.getEntityData() as ArtifactOutputPackagingElementEntityData

    override fun getEntityClass(): Class<ArtifactOutputPackagingElementEntity> = ArtifactOutputPackagingElementEntity::class.java
  }
}

class ArtifactOutputPackagingElementEntityData : WorkspaceEntityData<ArtifactOutputPackagingElementEntity>(), SoftLinkable {
  var artifact: ArtifactId? = null


  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    val optionalLink_artifact = artifact
    if (optionalLink_artifact != null) {
      result.add(optionalLink_artifact)
    }
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    val optionalLink_artifact = artifact
    if (optionalLink_artifact != null) {
      index.index(this, optionalLink_artifact)
    }
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    val optionalLink_artifact = artifact
    if (optionalLink_artifact != null) {
      val removedItem_optionalLink_artifact = mutablePreviousSet.remove(optionalLink_artifact)
      if (!removedItem_optionalLink_artifact) {
        index.index(this, optionalLink_artifact)
      }
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    var artifact_data_optional = if (artifact != null) {
      val artifact___data = if (artifact!! == oldLink) {
        changed = true
        newLink as ArtifactId
      }
      else {
        null
      }
      artifact___data
    }
    else {
      null
    }
    if (artifact_data_optional != null) {
      artifact = artifact_data_optional
    }
    return changed
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ArtifactOutputPackagingElementEntity> {
    val modifiable = ArtifactOutputPackagingElementEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ArtifactOutputPackagingElementEntity {
    return getCached(snapshot) {
      val entity = ArtifactOutputPackagingElementEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ArtifactOutputPackagingElementEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return ArtifactOutputPackagingElementEntity(entitySource) {
      this.artifact = this@ArtifactOutputPackagingElementEntityData.artifact
      this.parentEntity = parents.filterIsInstance<CompositePackagingElementEntity>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ArtifactOutputPackagingElementEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.artifact != other.artifact) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ArtifactOutputPackagingElementEntityData

    if (this.artifact != other.artifact) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + artifact.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + artifact.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.add(ArtifactId::class.java)
    collector.sameForAllEntities = true
  }
}
