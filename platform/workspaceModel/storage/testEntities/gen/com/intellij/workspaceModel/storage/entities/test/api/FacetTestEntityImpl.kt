// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class FacetTestEntityImpl : FacetTestEntity, WorkspaceEntityBase() {

  companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleTestEntity::class.java, FacetTestEntity::class.java,
                                                                          ConnectionId.ConnectionType.ONE_TO_MANY, false)

    val connections = listOf<ConnectionId>(
      MODULE_CONNECTION_ID,
    )

  }

  @JvmField
  var _data: String? = null
  override val data: String
    get() = _data!!

  @JvmField
  var _moreData: String? = null
  override val moreData: String
    get() = _moreData!!

  override val module: ModuleTestEntity
    get() = snapshot.extractOneToManyParent(MODULE_CONNECTION_ID, this)!!

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: FacetTestEntityData?) : ModifiableWorkspaceEntityBase<FacetTestEntity>(), FacetTestEntity.Builder {
    constructor() : this(FacetTestEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity FacetTestEntity is already created in a different builder")
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
      if (!getEntityData().isDataInitialized()) {
        error("Field FacetTestEntity#data should be initialized")
      }
      if (!getEntityData().isMoreDataInitialized()) {
        error("Field FacetTestEntity#moreData should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
          error("Field FacetTestEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field FacetTestEntity#module should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as FacetTestEntity
      this.entitySource = dataSource.entitySource
      this.data = dataSource.data
      this.moreData = dataSource.moreData
      if (parents != null) {
        this.module = parents.filterIsInstance<ModuleTestEntity>().single()
      }
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override var data: String
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData().data = value
        changedProperty.add("data")
      }

    override var moreData: String
      get() = getEntityData().moreData
      set(value) {
        checkModificationAllowed()
        getEntityData().moreData = value
        changedProperty.add("moreData")
      }

    override var module: ModuleTestEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyParent(MODULE_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                  MODULE_CONNECTION_ID)]!! as ModuleTestEntity
        }
        else {
          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleTestEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(MODULE_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
        }
        changedProperty.add("module")
      }

    override fun getEntityData(): FacetTestEntityData = result ?: super.getEntityData() as FacetTestEntityData
    override fun getEntityClass(): Class<FacetTestEntity> = FacetTestEntity::class.java
  }
}

class FacetTestEntityData : WorkspaceEntityData.WithCalculablePersistentId<FacetTestEntity>() {
  lateinit var data: String
  lateinit var moreData: String

  fun isDataInitialized(): Boolean = ::data.isInitialized
  fun isMoreDataInitialized(): Boolean = ::moreData.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<FacetTestEntity> {
    val modifiable = FacetTestEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): FacetTestEntity {
    val entity = FacetTestEntityImpl()
    entity._data = data
    entity._moreData = moreData
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
  }

  override fun persistentId(): PersistentEntityId<*> {
    return FacetTestEntityPersistentId(data)
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return FacetTestEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return FacetTestEntity(data, moreData, entitySource) {
      this.module = parents.filterIsInstance<ModuleTestEntity>().single()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleTestEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as FacetTestEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    if (this.moreData != other.moreData) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as FacetTestEntityData

    if (this.data != other.data) return false
    if (this.moreData != other.moreData) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + moreData.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + moreData.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
