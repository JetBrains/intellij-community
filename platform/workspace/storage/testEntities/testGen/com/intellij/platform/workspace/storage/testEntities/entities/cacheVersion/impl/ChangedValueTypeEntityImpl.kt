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
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedValueTypeEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChangedValueTypeEntityImpl(private val dataSource: ChangedValueTypeEntityData) : ChangedValueTypeEntity,
                                                                                                WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val type: String
    get() {
      readField("type")
      return dataSource.type
    }

  override val someKey: Int
    get() {
      readField("someKey")
      return dataSource.someKey
    }
  override val text: List<String>
    get() {
      readField("text")
      return dataSource.text
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ChangedValueTypeEntityData?) :
    ModifiableWorkspaceEntityBase<ChangedValueTypeEntity, ChangedValueTypeEntityData>(result), ChangedValueTypeEntity.Builder {
    internal constructor() : this(ChangedValueTypeEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ChangedValueTypeEntity is already created in a different builder")
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
      if (!getEntityData().isTypeInitialized()) {
        error("Field ChangedValueTypeEntity#type should be initialized")
      }
      if (!getEntityData().isTextInitialized()) {
        error("Field ChangedValueTypeEntity#text should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_text = getEntityData().text
      if (collection_text is MutableWorkspaceList<*>) {
        collection_text.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ChangedValueTypeEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.type != dataSource.type) this.type = dataSource.type
      if (this.someKey != dataSource.someKey) this.someKey = dataSource.someKey
      if (this.text != dataSource.text) this.text = dataSource.text.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var type: String
      get() = getEntityData().type
      set(value) {
        checkModificationAllowed()
        getEntityData(true).type = value
        changedProperty.add("type")
      }

    override var someKey: Int
      get() = getEntityData().someKey
      set(value) {
        checkModificationAllowed()
        getEntityData(true).someKey = value
        changedProperty.add("someKey")
      }

    private val textUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("text")
    }
    override var text: MutableList<String>
      get() {
        val collection_text = getEntityData().text
        if (collection_text !is MutableWorkspaceList) return collection_text
        if (diff == null || modifiable.get()) {
          collection_text.setModificationUpdateAction(textUpdater)
        }
        else {
          collection_text.cleanModificationUpdateAction()
        }
        return collection_text
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).text = value
        textUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<ChangedValueTypeEntity> = ChangedValueTypeEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ChangedValueTypeEntityData : WorkspaceEntityData<ChangedValueTypeEntity>() {
  lateinit var type: String
  var someKey: Int = 0
  lateinit var text: MutableList<String>

  internal fun isTypeInitialized(): Boolean = ::type.isInitialized

  internal fun isTextInitialized(): Boolean = ::text.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ChangedValueTypeEntity> {
    val modifiable = ChangedValueTypeEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ChangedValueTypeEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ChangedValueTypeEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedValueTypeEntity"
    ) as EntityMetadata
  }

  override fun clone(): ChangedValueTypeEntityData {
    val clonedEntity = super.clone()
    clonedEntity as ChangedValueTypeEntityData
    clonedEntity.text = clonedEntity.text.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ChangedValueTypeEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ChangedValueTypeEntity(type, someKey, text, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ChangedValueTypeEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.type != other.type) return false
    if (this.someKey != other.someKey) return false
    if (this.text != other.text) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ChangedValueTypeEntityData

    if (this.type != other.type) return false
    if (this.someKey != other.someKey) return false
    if (this.text != other.text) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + type.hashCode()
    result = 31 * result + someKey.hashCode()
    result = 31 * result + text.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + type.hashCode()
    result = 31 * result + someKey.hashCode()
    result = 31 * result + text.hashCode()
    return result
  }
}
