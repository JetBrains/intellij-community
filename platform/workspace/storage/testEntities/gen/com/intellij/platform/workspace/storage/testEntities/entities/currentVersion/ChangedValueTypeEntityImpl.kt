// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

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
open class ChangedValueTypeEntityImpl(private val dataSource: ChangedValueTypeEntityData) : ChangedValueTypeEntity, WorkspaceEntityBase(
  dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val type: String
    get() = dataSource.type

  override val someKey: String
    get() = dataSource.someKey

  override val text: List<String>
    get() = dataSource.text

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  class Builder(result: ChangedValueTypeEntityData?) : ModifiableWorkspaceEntityBase<ChangedValueTypeEntity, ChangedValueTypeEntityData>(
    result), ChangedValueTypeEntity.Builder {
    constructor() : this(ChangedValueTypeEntityData())

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
      if (!getEntityData().isTypeInitialized()) {
        error("Field ChangedValueTypeEntity#type should be initialized")
      }
      if (!getEntityData().isSomeKeyInitialized()) {
        error("Field ChangedValueTypeEntity#someKey should be initialized")
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

    override var someKey: String
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

class ChangedValueTypeEntityData : WorkspaceEntityData<ChangedValueTypeEntity>() {
  lateinit var type: String
  lateinit var someKey: String
  lateinit var text: MutableList<String>

  internal fun isTypeInitialized(): Boolean = ::type.isInitialized
  internal fun isSomeKeyInitialized(): Boolean = ::someKey.isInitialized
  internal fun isTextInitialized(): Boolean = ::text.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ChangedValueTypeEntity> {
    val modifiable = ChangedValueTypeEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ChangedValueTypeEntity {
    return getCached(snapshot) {
      val entity = ChangedValueTypeEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedValueTypeEntity") as EntityMetadata
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

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
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
