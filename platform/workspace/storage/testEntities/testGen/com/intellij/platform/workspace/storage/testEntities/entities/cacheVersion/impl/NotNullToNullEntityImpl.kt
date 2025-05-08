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
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.NotNullToNullEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class NotNullToNullEntityImpl(private val dataSource: NotNullToNullEntityData) : NotNullToNullEntity,
                                                                                          WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val nullInt: Int?
    get() {
      readField("nullInt")
      return dataSource.nullInt
    }
  override val notNullString: String
    get() {
      readField("notNullString")
      return dataSource.notNullString
    }

  override val notNullList: List<Int>
    get() {
      readField("notNullList")
      return dataSource.notNullList
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: NotNullToNullEntityData?) :
    ModifiableWorkspaceEntityBase<NotNullToNullEntity, NotNullToNullEntityData>(result), NotNullToNullEntity.Builder {
    internal constructor() : this(NotNullToNullEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity NotNullToNullEntity is already created in a different builder")
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
      if (!getEntityData().isNotNullStringInitialized()) {
        error("Field NotNullToNullEntity#notNullString should be initialized")
      }
      if (!getEntityData().isNotNullListInitialized()) {
        error("Field NotNullToNullEntity#notNullList should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_notNullList = getEntityData().notNullList
      if (collection_notNullList is MutableWorkspaceList<*>) {
        collection_notNullList.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as NotNullToNullEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.nullInt != dataSource?.nullInt) this.nullInt = dataSource.nullInt
      if (this.notNullString != dataSource.notNullString) this.notNullString = dataSource.notNullString
      if (this.notNullList != dataSource.notNullList) this.notNullList = dataSource.notNullList.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var nullInt: Int??
      get() = getEntityData().nullInt
      set(value) {
        checkModificationAllowed()
        getEntityData(true).nullInt = value
        changedProperty.add("nullInt")
      }

    override var notNullString: String
      get() = getEntityData().notNullString
      set(value) {
        checkModificationAllowed()
        getEntityData(true).notNullString = value
        changedProperty.add("notNullString")
      }

    private val notNullListUpdater: (value: List<Int>) -> Unit = { value ->

      changedProperty.add("notNullList")
    }
    override var notNullList: MutableList<Int>
      get() {
        val collection_notNullList = getEntityData().notNullList
        if (collection_notNullList !is MutableWorkspaceList) return collection_notNullList
        if (diff == null || modifiable.get()) {
          collection_notNullList.setModificationUpdateAction(notNullListUpdater)
        }
        else {
          collection_notNullList.cleanModificationUpdateAction()
        }
        return collection_notNullList
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).notNullList = value
        notNullListUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<NotNullToNullEntity> = NotNullToNullEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class NotNullToNullEntityData : WorkspaceEntityData<NotNullToNullEntity>() {
  var nullInt: Int? = null
  lateinit var notNullString: String
  lateinit var notNullList: MutableList<Int>

  internal fun isNotNullStringInitialized(): Boolean = ::notNullString.isInitialized
  internal fun isNotNullListInitialized(): Boolean = ::notNullList.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<NotNullToNullEntity> {
    val modifiable = NotNullToNullEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): NotNullToNullEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = NotNullToNullEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.NotNullToNullEntity"
    ) as EntityMetadata
  }

  override fun clone(): NotNullToNullEntityData {
    val clonedEntity = super.clone()
    clonedEntity as NotNullToNullEntityData
    clonedEntity.notNullList = clonedEntity.notNullList.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return NotNullToNullEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return NotNullToNullEntity(notNullString, notNullList, entitySource) {
      this.nullInt = this@NotNullToNullEntityData.nullInt
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as NotNullToNullEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.nullInt != other.nullInt) return false
    if (this.notNullString != other.notNullString) return false
    if (this.notNullList != other.notNullList) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as NotNullToNullEntityData

    if (this.nullInt != other.nullInt) return false
    if (this.notNullString != other.notNullString) return false
    if (this.notNullList != other.notNullList) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + nullInt.hashCode()
    result = 31 * result + notNullString.hashCode()
    result = 31 * result + notNullList.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + nullInt.hashCode()
    result = 31 * result + notNullString.hashCode()
    result = 31 * result + notNullList.hashCode()
    return result
  }
}
