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
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneParentEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class OptionalOneToOneChildEntityImpl(private val dataSource: OptionalOneToOneChildEntityData) : OptionalOneToOneChildEntity,
                                                                                                          WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val PARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(
      OptionalOneToOneParentEntity::class.java, OptionalOneToOneChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, true
    )

    private val connections = listOf<ConnectionId>(
      PARENT_CONNECTION_ID,
    )

  }

  override val data: String
    get() {
      readField("data")
      return dataSource.data
    }

  override val parent: OptionalOneToOneParentEntity?
    get() = snapshot.extractOneToOneParent(PARENT_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: OptionalOneToOneChildEntityData?) :
    ModifiableWorkspaceEntityBase<OptionalOneToOneChildEntity, OptionalOneToOneChildEntityData>(result),
    OptionalOneToOneChildEntity.Builder {
    internal constructor() : this(OptionalOneToOneChildEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity OptionalOneToOneChildEntity is already created in a different builder")
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
      if (!getEntityData().isDataInitialized()) {
        error("Field OptionalOneToOneChildEntity#data should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as OptionalOneToOneChildEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.data != dataSource.data) this.data = dataSource.data
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var data: String
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData(true).data = value
        changedProperty.add("data")
      }

    override var parent: OptionalOneToOneParentEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(
            PARENT_CONNECTION_ID, this
          ) as? OptionalOneToOneParentEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] as? OptionalOneToOneParentEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] as? OptionalOneToOneParentEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, PARENT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(PARENT_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, PARENT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] = value
        }
        changedProperty.add("parent")
      }

    override fun getEntityClass(): Class<OptionalOneToOneChildEntity> = OptionalOneToOneChildEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class OptionalOneToOneChildEntityData : WorkspaceEntityData<OptionalOneToOneChildEntity>() {
  lateinit var data: String

  internal fun isDataInitialized(): Boolean = ::data.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<OptionalOneToOneChildEntity> {
    val modifiable = OptionalOneToOneChildEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): OptionalOneToOneChildEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = OptionalOneToOneChildEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneChildEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return OptionalOneToOneChildEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return OptionalOneToOneChildEntity(data, entitySource) {
      this.parent = parents.filterIsInstance<OptionalOneToOneParentEntity.Builder>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as OptionalOneToOneChildEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as OptionalOneToOneChildEntityData

    if (this.data != other.data) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + data.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + data.hashCode()
    return result
  }
}
