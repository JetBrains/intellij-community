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
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.MutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ArtifactsOrderEntityImpl : ArtifactsOrderEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  @JvmField
  var _orderOfArtifacts: List<String>? = null
  override val orderOfArtifacts: List<String>
    get() = _orderOfArtifacts!!

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: ArtifactsOrderEntityData?) : ModifiableWorkspaceEntityBase<ArtifactsOrderEntity>(), ArtifactsOrderEntity.Builder {
    constructor() : this(ArtifactsOrderEntityData())

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
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
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

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ArtifactsOrderEntity
      this.entitySource = dataSource.entitySource
      this.orderOfArtifacts = dataSource.orderOfArtifacts.toMutableList()
      if (parents != null) {
      }
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    private val orderOfArtifactsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("orderOfArtifacts")
    }
    override var orderOfArtifacts: MutableList<String>
      get() {
        val collection_orderOfArtifacts = getEntityData().orderOfArtifacts
        if (collection_orderOfArtifacts !is MutableWorkspaceList) return collection_orderOfArtifacts
        collection_orderOfArtifacts.setModificationUpdateAction(orderOfArtifactsUpdater)
        return collection_orderOfArtifacts
      }
      set(value) {
        checkModificationAllowed()
        getEntityData().orderOfArtifacts = value
        orderOfArtifactsUpdater.invoke(value)
      }

    override fun getEntityData(): ArtifactsOrderEntityData = result ?: super.getEntityData() as ArtifactsOrderEntityData
    override fun getEntityClass(): Class<ArtifactsOrderEntity> = ArtifactsOrderEntity::class.java
  }
}

class ArtifactsOrderEntityData : WorkspaceEntityData<ArtifactsOrderEntity>() {
  lateinit var orderOfArtifacts: MutableList<String>

  fun isOrderOfArtifactsInitialized(): Boolean = ::orderOfArtifacts.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ArtifactsOrderEntity> {
    val modifiable = ArtifactsOrderEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ArtifactsOrderEntity {
    val entity = ArtifactsOrderEntityImpl()
    entity._orderOfArtifacts = orderOfArtifacts.toList()
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
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

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return ArtifactsOrderEntity(orderOfArtifacts, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as ArtifactsOrderEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.orderOfArtifacts != other.orderOfArtifacts) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

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

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    this.orderOfArtifacts?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
