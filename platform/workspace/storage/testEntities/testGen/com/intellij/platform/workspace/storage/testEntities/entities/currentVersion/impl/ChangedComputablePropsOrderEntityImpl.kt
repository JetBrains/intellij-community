// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropsOrderEntity
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropsOrderEntityId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChangedComputablePropsOrderEntityImpl(private val dataSource: ChangedComputablePropsOrderEntityData) :
  ChangedComputablePropsOrderEntity, WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val symbolicId: ChangedComputablePropsOrderEntityId = super.symbolicId

  override val someKey: Int
    get() {
      readField("someKey")
      return dataSource.someKey
    }
  override val names: List<String>
    get() {
      readField("names")
      return dataSource.names
    }

  override val value: Int
    get() {
      readField("value")
      return dataSource.value
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ChangedComputablePropsOrderEntityData?) :
    ModifiableWorkspaceEntityBase<ChangedComputablePropsOrderEntity, ChangedComputablePropsOrderEntityData>(result),
    ChangedComputablePropsOrderEntity.Builder {
    internal constructor() : this(ChangedComputablePropsOrderEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ChangedComputablePropsOrderEntity is already created in a different builder")
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
      if (!getEntityData().isNamesInitialized()) {
        error("Field ChangedComputablePropsOrderEntity#names should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_names = getEntityData().names
      if (collection_names is MutableWorkspaceList<*>) {
        collection_names.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ChangedComputablePropsOrderEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.someKey != dataSource.someKey) this.someKey = dataSource.someKey
      if (this.names != dataSource.names) this.names = dataSource.names.toMutableList()
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

    override var someKey: Int
      get() = getEntityData().someKey
      set(value) {
        checkModificationAllowed()
        getEntityData(true).someKey = value
        changedProperty.add("someKey")
      }

    private val namesUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("names")
    }
    override var names: MutableList<String>
      get() {
        val collection_names = getEntityData().names
        if (collection_names !is MutableWorkspaceList) return collection_names
        if (diff == null || modifiable.get()) {
          collection_names.setModificationUpdateAction(namesUpdater)
        }
        else {
          collection_names.cleanModificationUpdateAction()
        }
        return collection_names
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).names = value
        namesUpdater.invoke(value)
      }

    override var value: Int
      get() = getEntityData().value
      set(value) {
        checkModificationAllowed()
        getEntityData(true).value = value
        changedProperty.add("value")
      }

    override fun getEntityClass(): Class<ChangedComputablePropsOrderEntity> = ChangedComputablePropsOrderEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ChangedComputablePropsOrderEntityData : WorkspaceEntityData<ChangedComputablePropsOrderEntity>() {
  var someKey: Int = 0
  lateinit var names: MutableList<String>
  var value: Int = 0


  internal fun isNamesInitialized(): Boolean = ::names.isInitialized


  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ChangedComputablePropsOrderEntity> {
    val modifiable = ChangedComputablePropsOrderEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ChangedComputablePropsOrderEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ChangedComputablePropsOrderEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropsOrderEntity"
    ) as EntityMetadata
  }

  override fun clone(): ChangedComputablePropsOrderEntityData {
    val clonedEntity = super.clone()
    clonedEntity as ChangedComputablePropsOrderEntityData
    clonedEntity.names = clonedEntity.names.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ChangedComputablePropsOrderEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ChangedComputablePropsOrderEntity(someKey, names, value, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ChangedComputablePropsOrderEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.someKey != other.someKey) return false
    if (this.names != other.names) return false
    if (this.value != other.value) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ChangedComputablePropsOrderEntityData

    if (this.someKey != other.someKey) return false
    if (this.names != other.names) return false
    if (this.value != other.value) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + someKey.hashCode()
    result = 31 * result + names.hashCode()
    result = 31 * result + value.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + someKey.hashCode()
    result = 31 * result + names.hashCode()
    result = 31 * result + value.hashCode()
    return result
  }
}
