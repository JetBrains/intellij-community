// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl

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
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropsOrderDataClass
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropsOrderEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChangedPropsOrderEntityImpl(private val dataSource: ChangedPropsOrderEntityData) : ChangedPropsOrderEntity,
                                                                                                  WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val version: Int
    get() {
      readField("version")
      return dataSource.version
    }
  override val string: String
    get() {
      readField("string")
      return dataSource.string
    }

  override val data: ChangedPropsOrderDataClass
    get() {
      readField("data")
      return dataSource.data
    }

  override val list: List<Set<Int>>
    get() {
      readField("list")
      return dataSource.list
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ChangedPropsOrderEntityData?) :
    ModifiableWorkspaceEntityBase<ChangedPropsOrderEntity, ChangedPropsOrderEntityData>(result), ChangedPropsOrderEntity.Builder {
    internal constructor() : this(ChangedPropsOrderEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ChangedPropsOrderEntity is already created in a different builder")
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
      if (!getEntityData().isStringInitialized()) {
        error("Field ChangedPropsOrderEntity#string should be initialized")
      }
      if (!getEntityData().isDataInitialized()) {
        error("Field ChangedPropsOrderEntity#data should be initialized")
      }
      if (!getEntityData().isListInitialized()) {
        error("Field ChangedPropsOrderEntity#list should be initialized")
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
      dataSource as ChangedPropsOrderEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.version != dataSource.version) this.version = dataSource.version
      if (this.string != dataSource.string) this.string = dataSource.string
      if (this.data != dataSource.data) this.data = dataSource.data
      if (this.list != dataSource.list) this.list = dataSource.list.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var version: Int
      get() = getEntityData().version
      set(value) {
        checkModificationAllowed()
        getEntityData(true).version = value
        changedProperty.add("version")
      }

    override var string: String
      get() = getEntityData().string
      set(value) {
        checkModificationAllowed()
        getEntityData(true).string = value
        changedProperty.add("string")
      }

    override var data: ChangedPropsOrderDataClass
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData(true).data = value
        changedProperty.add("data")

      }

    private val listUpdater: (value: List<Set<Int>>) -> Unit = { value ->

      changedProperty.add("list")
    }
    override var list: MutableList<Set<Int>>
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

    override fun getEntityClass(): Class<ChangedPropsOrderEntity> = ChangedPropsOrderEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ChangedPropsOrderEntityData : WorkspaceEntityData<ChangedPropsOrderEntity>() {
  var version: Int = 0
  lateinit var string: String
  lateinit var data: ChangedPropsOrderDataClass
  lateinit var list: MutableList<Set<Int>>


  internal fun isStringInitialized(): Boolean = ::string.isInitialized
  internal fun isDataInitialized(): Boolean = ::data.isInitialized
  internal fun isListInitialized(): Boolean = ::list.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ChangedPropsOrderEntity> {
    val modifiable = ChangedPropsOrderEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ChangedPropsOrderEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ChangedPropsOrderEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropsOrderEntity"
    ) as EntityMetadata
  }

  override fun clone(): ChangedPropsOrderEntityData {
    val clonedEntity = super.clone()
    clonedEntity as ChangedPropsOrderEntityData
    clonedEntity.list = clonedEntity.list.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ChangedPropsOrderEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ChangedPropsOrderEntity(version, string, data, list, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ChangedPropsOrderEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.version != other.version) return false
    if (this.string != other.string) return false
    if (this.data != other.data) return false
    if (this.list != other.list) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ChangedPropsOrderEntityData

    if (this.version != other.version) return false
    if (this.string != other.string) return false
    if (this.data != other.data) return false
    if (this.list != other.list) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + string.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + list.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + string.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + list.hashCode()
    return result
  }
}
