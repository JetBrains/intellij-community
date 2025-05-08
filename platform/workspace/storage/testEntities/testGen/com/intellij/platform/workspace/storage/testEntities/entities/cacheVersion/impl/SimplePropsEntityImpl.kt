// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceSet
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimplePropsEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SimplePropsEntityImpl(private val dataSource: SimplePropsEntityData) : SimplePropsEntity, WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val text: String
    get() {
      readField("text")
      return dataSource.text
    }

  override val list: List<Int>
    get() {
      readField("list")
      return dataSource.list
    }

  override val set: Set<List<String>>
    get() {
      readField("set")
      return dataSource.set
    }

  override val map: Map<Set<Int>, List<String>>
    get() {
      readField("map")
      return dataSource.map
    }
  override val bool: Boolean
    get() {
      readField("bool")
      return dataSource.bool
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: SimplePropsEntityData?) : ModifiableWorkspaceEntityBase<SimplePropsEntity, SimplePropsEntityData>(result),
                                                           SimplePropsEntity.Builder {
    internal constructor() : this(SimplePropsEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SimplePropsEntity is already created in a different builder")
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
      if (!getEntityData().isTextInitialized()) {
        error("Field SimplePropsEntity#text should be initialized")
      }
      if (!getEntityData().isListInitialized()) {
        error("Field SimplePropsEntity#list should be initialized")
      }
      if (!getEntityData().isSetInitialized()) {
        error("Field SimplePropsEntity#set should be initialized")
      }
      if (!getEntityData().isMapInitialized()) {
        error("Field SimplePropsEntity#map should be initialized")
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
      val collection_set = getEntityData().set
      if (collection_set is MutableWorkspaceSet<*>) {
        collection_set.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SimplePropsEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.text != dataSource.text) this.text = dataSource.text
      if (this.list != dataSource.list) this.list = dataSource.list.toMutableList()
      if (this.set != dataSource.set) this.set = dataSource.set.toMutableSet()
      if (this.map != dataSource.map) this.map = dataSource.map.toMutableMap()
      if (this.bool != dataSource.bool) this.bool = dataSource.bool
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var text: String
      get() = getEntityData().text
      set(value) {
        checkModificationAllowed()
        getEntityData(true).text = value
        changedProperty.add("text")
      }

    private val listUpdater: (value: List<Int>) -> Unit = { value ->

      changedProperty.add("list")
    }
    override var list: MutableList<Int>
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

    private val setUpdater: (value: Set<List<String>>) -> Unit = { value ->

      changedProperty.add("set")
    }
    override var set: MutableSet<List<String>>
      get() {
        val collection_set = getEntityData().set
        if (collection_set !is MutableWorkspaceSet) return collection_set
        if (diff == null || modifiable.get()) {
          collection_set.setModificationUpdateAction(setUpdater)
        }
        else {
          collection_set.cleanModificationUpdateAction()
        }
        return collection_set
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).set = value
        setUpdater.invoke(value)
      }

    override var map: Map<Set<Int>, List<String>>
      get() = getEntityData().map
      set(value) {
        checkModificationAllowed()
        getEntityData(true).map = value
        changedProperty.add("map")
      }

    override var bool: Boolean
      get() = getEntityData().bool
      set(value) {
        checkModificationAllowed()
        getEntityData(true).bool = value
        changedProperty.add("bool")
      }

    override fun getEntityClass(): Class<SimplePropsEntity> = SimplePropsEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SimplePropsEntityData : WorkspaceEntityData<SimplePropsEntity>() {
  lateinit var text: String
  lateinit var list: MutableList<Int>
  lateinit var set: MutableSet<List<String>>
  lateinit var map: Map<Set<Int>, List<String>>
  var bool: Boolean = false

  internal fun isTextInitialized(): Boolean = ::text.isInitialized
  internal fun isListInitialized(): Boolean = ::list.isInitialized
  internal fun isSetInitialized(): Boolean = ::set.isInitialized
  internal fun isMapInitialized(): Boolean = ::map.isInitialized


  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SimplePropsEntity> {
    val modifiable = SimplePropsEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): SimplePropsEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = SimplePropsEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimplePropsEntity"
    ) as EntityMetadata
  }

  override fun clone(): SimplePropsEntityData {
    val clonedEntity = super.clone()
    clonedEntity as SimplePropsEntityData
    clonedEntity.list = clonedEntity.list.toMutableWorkspaceList()
    clonedEntity.set = clonedEntity.set.toMutableWorkspaceSet()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SimplePropsEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return SimplePropsEntity(text, list, set, map, bool, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SimplePropsEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.text != other.text) return false
    if (this.list != other.list) return false
    if (this.set != other.set) return false
    if (this.map != other.map) return false
    if (this.bool != other.bool) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SimplePropsEntityData

    if (this.text != other.text) return false
    if (this.list != other.list) return false
    if (this.set != other.set) return false
    if (this.map != other.map) return false
    if (this.bool != other.bool) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + text.hashCode()
    result = 31 * result + list.hashCode()
    result = 31 * result + set.hashCode()
    result = 31 * result + map.hashCode()
    result = 31 * result + bool.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + text.hashCode()
    result = 31 * result + list.hashCode()
    result = 31 * result + set.hashCode()
    result = 31 * result + map.hashCode()
    result = 31 * result + bool.hashCode()
    return result
  }
}
