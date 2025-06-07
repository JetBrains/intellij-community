// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.FacetsOrderEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class FacetsOrderEntityImpl(private val dataSource: FacetsOrderEntityData) : FacetsOrderEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val MODULEENTITY_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleEntity::class.java, FacetsOrderEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)

    private val connections = listOf<ConnectionId>(
      MODULEENTITY_CONNECTION_ID,
    )

  }

  override val orderOfFacets: List<String>
    get() {
      readField("orderOfFacets")
      return dataSource.orderOfFacets
    }

  override val moduleEntity: ModuleEntity
    get() = snapshot.extractOneToOneParent(MODULEENTITY_CONNECTION_ID, this)!!

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: FacetsOrderEntityData?) : ModifiableWorkspaceEntityBase<FacetsOrderEntity, FacetsOrderEntityData>(result),
                                                           FacetsOrderEntity.Builder {
    internal constructor() : this(FacetsOrderEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity FacetsOrderEntity is already created in a different builder")
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
      if (!getEntityData().isOrderOfFacetsInitialized()) {
        error("Field FacetsOrderEntity#orderOfFacets should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToOneParent<WorkspaceEntityBase>(MODULEENTITY_CONNECTION_ID, this) == null) {
          error("Field FacetsOrderEntity#moduleEntity should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULEENTITY_CONNECTION_ID)] == null) {
          error("Field FacetsOrderEntity#moduleEntity should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_orderOfFacets = getEntityData().orderOfFacets
      if (collection_orderOfFacets is MutableWorkspaceList<*>) {
        collection_orderOfFacets.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as FacetsOrderEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.orderOfFacets != dataSource.orderOfFacets) this.orderOfFacets = dataSource.orderOfFacets.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    private val orderOfFacetsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("orderOfFacets")
    }
    override var orderOfFacets: MutableList<String>
      get() {
        val collection_orderOfFacets = getEntityData().orderOfFacets
        if (collection_orderOfFacets !is MutableWorkspaceList) return collection_orderOfFacets
        if (diff == null || modifiable.get()) {
          collection_orderOfFacets.setModificationUpdateAction(orderOfFacetsUpdater)
        }
        else {
          collection_orderOfFacets.cleanModificationUpdateAction()
        }
        return collection_orderOfFacets
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).orderOfFacets = value
        orderOfFacetsUpdater.invoke(value)
      }

    override var moduleEntity: ModuleEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(MODULEENTITY_CONNECTION_ID, this) as? ModuleEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, MODULEENTITY_CONNECTION_ID)]!! as ModuleEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, MODULEENTITY_CONNECTION_ID)]!! as ModuleEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, MODULEENTITY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(MODULEENTITY_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, MODULEENTITY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, MODULEENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("moduleEntity")
      }

    override fun getEntityClass(): Class<FacetsOrderEntity> = FacetsOrderEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class FacetsOrderEntityData : WorkspaceEntityData<FacetsOrderEntity>() {
  lateinit var orderOfFacets: MutableList<String>

  internal fun isOrderOfFacetsInitialized(): Boolean = ::orderOfFacets.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<FacetsOrderEntity> {
    val modifiable = FacetsOrderEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): FacetsOrderEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = FacetsOrderEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.jps.entities.FacetsOrderEntity") as EntityMetadata
  }

  override fun clone(): FacetsOrderEntityData {
    val clonedEntity = super.clone()
    clonedEntity as FacetsOrderEntityData
    clonedEntity.orderOfFacets = clonedEntity.orderOfFacets.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return FacetsOrderEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return FacetsOrderEntity(orderOfFacets, entitySource) {
      parents.filterIsInstance<ModuleEntity.Builder>().singleOrNull()?.let { this.moduleEntity = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as FacetsOrderEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.orderOfFacets != other.orderOfFacets) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as FacetsOrderEntityData

    if (this.orderOfFacets != other.orderOfFacets) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + orderOfFacets.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + orderOfFacets.hashCode()
    return result
  }
}
