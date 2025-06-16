// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

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
import com.intellij.platform.workspace.storage.testEntities.entities.CollectionFieldEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class CollectionFieldEntityImpl(private val dataSource: CollectionFieldEntityData) : CollectionFieldEntity,
                                                                                              WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val versions: Set<Int>
    get() {
      readField("versions")
      return dataSource.versions
    }

  override val names: List<String>
    get() {
      readField("names")
      return dataSource.names
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: CollectionFieldEntityData?) :
    ModifiableWorkspaceEntityBase<CollectionFieldEntity, CollectionFieldEntityData>(result), CollectionFieldEntity.Builder {
    internal constructor() : this(CollectionFieldEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity CollectionFieldEntity is already created in a different builder")
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
      if (!getEntityData().isVersionsInitialized()) {
        error("Field CollectionFieldEntity#versions should be initialized")
      }
      if (!getEntityData().isNamesInitialized()) {
        error("Field CollectionFieldEntity#names should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_versions = getEntityData().versions
      if (collection_versions is MutableWorkspaceSet<*>) {
        collection_versions.cleanModificationUpdateAction()
      }
      val collection_names = getEntityData().names
      if (collection_names is MutableWorkspaceList<*>) {
        collection_names.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as CollectionFieldEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.versions != dataSource.versions) this.versions = dataSource.versions.toMutableSet()
      if (this.names != dataSource.names) this.names = dataSource.names.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    private val versionsUpdater: (value: Set<Int>) -> Unit = { value ->

      changedProperty.add("versions")
    }
    override var versions: MutableSet<Int>
      get() {
        val collection_versions = getEntityData().versions
        if (collection_versions !is MutableWorkspaceSet) return collection_versions
        if (diff == null || modifiable.get()) {
          collection_versions.setModificationUpdateAction(versionsUpdater)
        }
        else {
          collection_versions.cleanModificationUpdateAction()
        }
        return collection_versions
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).versions = value
        versionsUpdater.invoke(value)
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

    override fun getEntityClass(): Class<CollectionFieldEntity> = CollectionFieldEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class CollectionFieldEntityData : WorkspaceEntityData<CollectionFieldEntity>() {
  lateinit var versions: MutableSet<Int>
  lateinit var names: MutableList<String>

  internal fun isVersionsInitialized(): Boolean = ::versions.isInitialized
  internal fun isNamesInitialized(): Boolean = ::names.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<CollectionFieldEntity> {
    val modifiable = CollectionFieldEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): CollectionFieldEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = CollectionFieldEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.CollectionFieldEntity"
    ) as EntityMetadata
  }

  override fun clone(): CollectionFieldEntityData {
    val clonedEntity = super.clone()
    clonedEntity as CollectionFieldEntityData
    clonedEntity.versions = clonedEntity.versions.toMutableWorkspaceSet()
    clonedEntity.names = clonedEntity.names.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return CollectionFieldEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return CollectionFieldEntity(versions, names, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as CollectionFieldEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.versions != other.versions) return false
    if (this.names != other.names) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as CollectionFieldEntityData

    if (this.versions != other.versions) return false
    if (this.names != other.names) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + versions.hashCode()
    result = 31 * result + names.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + versions.hashCode()
    result = 31 * result + names.hashCode()
    return result
  }
}
