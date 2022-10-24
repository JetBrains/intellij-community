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
import com.intellij.workspaceModel.storage.impl.containers.MutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class FacetsOrderEntityImpl(val dataSource: FacetsOrderEntityData) : FacetsOrderEntity, WorkspaceEntityBase() {

  companion object {
    internal val MODULEENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, FacetsOrderEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ONE, false)

    val connections = listOf<ConnectionId>(
      MODULEENTITY_CONNECTION_ID,
    )

  }

  override val orderOfFacets: List<String>
    get() = dataSource.orderOfFacets

  override val moduleEntity: ModuleEntity
    get() = snapshot.extractOneToOneParent(MODULEENTITY_CONNECTION_ID, this)!!

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(var result: FacetsOrderEntityData?) : ModifiableWorkspaceEntityBase<FacetsOrderEntity>(), FacetsOrderEntity.Builder {
    constructor() : this(FacetsOrderEntityData())

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
      if (parents != null) {
        val moduleEntityNew = parents.filterIsInstance<ModuleEntity>().single()
        if ((this.moduleEntity as WorkspaceEntityBase).id != (moduleEntityNew as WorkspaceEntityBase).id) {
          this.moduleEntity = moduleEntityNew
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
        getEntityData().orderOfFacets = value
        orderOfFacetsUpdater.invoke(value)
      }

    override var moduleEntity: ModuleEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneParent(MODULEENTITY_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                       MODULEENTITY_CONNECTION_ID)]!! as ModuleEntity
        }
        else {
          this.entityLinks[EntityLink(false, MODULEENTITY_CONNECTION_ID)]!! as ModuleEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(true, MODULEENTITY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(MODULEENTITY_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(true, MODULEENTITY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, MODULEENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("moduleEntity")
      }

    override fun getEntityData(): FacetsOrderEntityData = result ?: super.getEntityData() as FacetsOrderEntityData
    override fun getEntityClass(): Class<FacetsOrderEntity> = FacetsOrderEntity::class.java
  }
}

class FacetsOrderEntityData : WorkspaceEntityData<FacetsOrderEntity>() {
  lateinit var orderOfFacets: MutableList<String>

  fun isOrderOfFacetsInitialized(): Boolean = ::orderOfFacets.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<FacetsOrderEntity> {
    val modifiable = FacetsOrderEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): FacetsOrderEntity {
    return getCached(snapshot) {
      val entity = FacetsOrderEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
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

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return FacetsOrderEntity(orderOfFacets, entitySource) {
      this.moduleEntity = parents.filterIsInstance<ModuleEntity>().single()
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

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    this.orderOfFacets?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
