// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(3)
open class ComputablePropEntityImpl(private val dataSource: ComputablePropEntityData) : ComputablePropEntity, WorkspaceEntityBase(
  dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val list: List<Map<List<Int?>, String>>
    get() = dataSource.list

  override val value: Int get() = dataSource.value

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  class Builder(result: ComputablePropEntityData?) : ModifiableWorkspaceEntityBase<ComputablePropEntity, ComputablePropEntityData>(
    result), ComputablePropEntity.Builder {
    constructor() : this(ComputablePropEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ComputablePropEntity is already created in a different builder")
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

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isListInitialized()) {
        error("Field ComputablePropEntity#list should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_list = getEntityData().list
      if (collection_list is MutableWorkspaceList<*>) {
        collection_list.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ComputablePropEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.list != dataSource.list) this.list = dataSource.list.toMutableList()
      if (this.value != dataSource.value) this.value = dataSource.value
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    private val listUpdater: (value: List<Map<List<Int?>, String>>) -> Unit = { value ->

      changedProperty.add("list")
    }
    override var list: MutableList<Map<List<Int?>, String>>
      get() {
        val collection_list = getEntityData().list
        if (collection_list !is MutableWorkspaceList) return collection_list
        if (diff == null || modifiable.get()) {
          collection_list.setModificationUpdateAction(listUpdater)
        }
        else {
          collection_list.cleanModificationUpdateAction()
        }
        return collection_list
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).list = value
        listUpdater.invoke(value)
      }

    override var value: Int
      get() = getEntityData().value
      set(value) {
        checkModificationAllowed()
        getEntityData(true).value = value
        changedProperty.add("value")
      }

    override fun getEntityClass(): Class<ComputablePropEntity> = ComputablePropEntity::class.java
  }
}

class ComputablePropEntityData : WorkspaceEntityData<ComputablePropEntity>() {
  lateinit var list: MutableList<Map<List<Int?>, String>>
  var value: Int = 0

  internal fun isListInitialized(): Boolean = ::list.isInitialized


  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ComputablePropEntity> {
    val modifiable = ComputablePropEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ComputablePropEntity {
    return getCached(snapshot) {
      val entity = ComputablePropEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ComputablePropEntity") as EntityMetadata
  }

  override fun clone(): ComputablePropEntityData {
    val clonedEntity = super.clone()
    clonedEntity as ComputablePropEntityData
    clonedEntity.list = clonedEntity.list.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ComputablePropEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return ComputablePropEntity(list, value, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ComputablePropEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.list != other.list) return false
    if (this.value != other.value) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ComputablePropEntityData

    if (this.list != other.list) return false
    if (this.value != other.value) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + list.hashCode()
    result = 31 * result + value.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + list.hashCode()
    result = 31 * result + value.hashCode()
    return result
  }
}
