// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities.impl

import com.intellij.java.workspace.entities.ArtifactsOrderEntity
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ArtifactsOrderEntityImpl(private val dataSource: ArtifactsOrderEntityData) : ArtifactsOrderEntity,
                                                                                            WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val orderOfArtifacts: List<String>
    get() {
      readField("orderOfArtifacts")
      return dataSource.orderOfArtifacts
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ArtifactsOrderEntityData?) :
    ModifiableWorkspaceEntityBase<ArtifactsOrderEntity, ArtifactsOrderEntityData>(result), ArtifactsOrderEntity.Builder {
    internal constructor() : this(ArtifactsOrderEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ArtifactsOrderEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isOrderOfArtifactsInitialized()) {
        error("Field ArtifactsOrderEntity#orderOfArtifacts should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_orderOfArtifacts = getEntityData().orderOfArtifacts
      if (collection_orderOfArtifacts is MutableWorkspaceList<*>) {
        collection_orderOfArtifacts.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ArtifactsOrderEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.orderOfArtifacts != dataSource.orderOfArtifacts) this.orderOfArtifacts = dataSource.orderOfArtifacts.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    private val orderOfArtifactsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("orderOfArtifacts")
    }
    override var orderOfArtifacts: MutableList<String>
      get() {
        val collection_orderOfArtifacts = getEntityData().orderOfArtifacts
        if (collection_orderOfArtifacts !is MutableWorkspaceList) return collection_orderOfArtifacts
        if (diff == null || modifiable.get()) {
          collection_orderOfArtifacts.setModificationUpdateAction(orderOfArtifactsUpdater)
        }
        else {
          collection_orderOfArtifacts.cleanModificationUpdateAction()
        }
        return collection_orderOfArtifacts
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).orderOfArtifacts = value
        orderOfArtifactsUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<ArtifactsOrderEntity> = ArtifactsOrderEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ArtifactsOrderEntityData : WorkspaceEntityData<ArtifactsOrderEntity>() {
  lateinit var orderOfArtifacts: MutableList<String>

  internal fun isOrderOfArtifactsInitialized(): Boolean = ::orderOfArtifacts.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ArtifactsOrderEntity> {
    val modifiable = ArtifactsOrderEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ArtifactsOrderEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ArtifactsOrderEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.workspace.entities.ArtifactsOrderEntity") as EntityMetadata
  }

  override fun clone(): ArtifactsOrderEntityData {
    val clonedEntity = super.clone()
    clonedEntity as ArtifactsOrderEntityData
    clonedEntity.orderOfArtifacts = clonedEntity.orderOfArtifacts.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ArtifactsOrderEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ArtifactsOrderEntity(orderOfArtifacts, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ArtifactsOrderEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.orderOfArtifacts != other.orderOfArtifacts) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ArtifactsOrderEntityData

    if (this.orderOfArtifacts != other.orderOfArtifacts) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + orderOfArtifacts.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + orderOfArtifacts.hashCode()
    return result
  }
}
