// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
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
open class FacetsOrderEntityImpl : FacetsOrderEntity, WorkspaceEntityBase() {

  companion object {
    internal val MODULEENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, FacetsOrderEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ONE, false)

    val connections = listOf<ConnectionId>(
      MODULEENTITY_CONNECTION_ID,
    )

  }

  @JvmField
  var _orderOfFacets: List<String>? = null
  override val orderOfFacets: List<String>
    get() = _orderOfFacets!!

  override val moduleEntity: ModuleEntity
    get() = snapshot.extractOneToOneParent(MODULEENTITY_CONNECTION_ID, this)!!

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: FacetsOrderEntityData?) : ModifiableWorkspaceEntityBase<FacetsOrderEntity>(), FacetsOrderEntity.Builder {
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

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isOrderOfFacetsInitialized()) {
        error("Field FacetsOrderEntity#orderOfFacets should be initialized")
      }
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field FacetsOrderEntity#entitySource should be initialized")
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


    private val orderOfFacetsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("orderOfFacets")
    }
    override var orderOfFacets: MutableList<String>
      get() {
        val collection_orderOfFacets = getEntityData().orderOfFacets
        if (collection_orderOfFacets !is MutableWorkspaceList) return collection_orderOfFacets
        collection_orderOfFacets.setModificationUpdateAction(orderOfFacetsUpdater)
        return collection_orderOfFacets
      }
      set(value) {
        checkModificationAllowed()
        getEntityData().orderOfFacets = value
        orderOfFacetsUpdater.invoke(value)
      }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

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

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<FacetsOrderEntity> {
    val modifiable = FacetsOrderEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): FacetsOrderEntity {
    val entity = FacetsOrderEntityImpl()
    entity._orderOfFacets = orderOfFacets.toList()
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
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

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as FacetsOrderEntityData

    if (this.orderOfFacets != other.orderOfFacets) return false
    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as FacetsOrderEntityData

    if (this.orderOfFacets != other.orderOfFacets) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + orderOfFacets.hashCode()
    return result
  }
}
