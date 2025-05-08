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
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToAbstractManyParent
import com.intellij.platform.workspace.storage.impl.updateOneToAbstractManyParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.ChildAbstractBaseEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ChildSecondEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ParentAbEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildSecondEntityImpl(private val dataSource: ChildSecondEntityData) : ChildSecondEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(
      ParentAbEntity::class.java, ChildAbstractBaseEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false
    )

    private val connections = listOf<ConnectionId>(
      PARENTENTITY_CONNECTION_ID,
    )

  }

  override val commonData: String
    get() {
      readField("commonData")
      return dataSource.commonData
    }

  override val parentEntity: ParentAbEntity
    get() = snapshot.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this)!!

  override val secondData: String
    get() {
      readField("secondData")
      return dataSource.secondData
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ChildSecondEntityData?) : ModifiableWorkspaceEntityBase<ChildSecondEntity, ChildSecondEntityData>(result),
                                                           ChildSecondEntity.Builder {
    internal constructor() : this(ChildSecondEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ChildSecondEntity is already created in a different builder")
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
      if (!getEntityData().isCommonDataInitialized()) {
        error("Field ChildAbstractBaseEntity#commonData should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToAbstractManyParent<WorkspaceEntityBase>(PARENTENTITY_CONNECTION_ID, this) == null) {
          error("Field ChildAbstractBaseEntity#parentEntity should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] == null) {
          error("Field ChildAbstractBaseEntity#parentEntity should be initialized")
        }
      }
      if (!getEntityData().isSecondDataInitialized()) {
        error("Field ChildSecondEntity#secondData should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ChildSecondEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.commonData != dataSource.commonData) this.commonData = dataSource.commonData
      if (this.secondData != dataSource.secondData) this.secondData = dataSource.secondData
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var commonData: String
      get() = getEntityData().commonData
      set(value) {
        checkModificationAllowed()
        getEntityData(true).commonData = value
        changedProperty.add("commonData")
      }

    override var parentEntity: ParentAbEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENTENTITY_CONNECTION_ID, this) as? ParentAbEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)]!! as ParentAbEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)]!! as ParentAbEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToAbstractManyParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("parentEntity")
      }

    override var secondData: String
      get() = getEntityData().secondData
      set(value) {
        checkModificationAllowed()
        getEntityData(true).secondData = value
        changedProperty.add("secondData")
      }

    override fun getEntityClass(): Class<ChildSecondEntity> = ChildSecondEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildSecondEntityData : WorkspaceEntityData<ChildSecondEntity>() {
  lateinit var commonData: String
  lateinit var secondData: String

  internal fun isCommonDataInitialized(): Boolean = ::commonData.isInitialized
  internal fun isSecondDataInitialized(): Boolean = ::secondData.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ChildSecondEntity> {
    val modifiable = ChildSecondEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ChildSecondEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ChildSecondEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.ChildSecondEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ChildSecondEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ChildSecondEntity(commonData, secondData, entitySource) {
      parents.filterIsInstance<ParentAbEntity.Builder>().singleOrNull()?.let { this.parentEntity = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ParentAbEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ChildSecondEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.commonData != other.commonData) return false
    if (this.secondData != other.secondData) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ChildSecondEntityData

    if (this.commonData != other.commonData) return false
    if (this.secondData != other.secondData) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + commonData.hashCode()
    result = 31 * result + secondData.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + commonData.hashCode()
    result = 31 * result + secondData.hashCode()
    return result
  }
}
