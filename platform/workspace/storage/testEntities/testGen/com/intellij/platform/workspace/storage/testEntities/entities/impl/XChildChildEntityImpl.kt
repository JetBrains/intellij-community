// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToManyParent
import com.intellij.platform.workspace.storage.impl.updateOneToManyParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class XChildChildEntityImpl(private val dataSource: XChildChildEntityData) : XChildChildEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val PARENT1_CONNECTION_ID: ConnectionId =
      ConnectionId.create(XParentEntity::class.java, XChildChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val PARENT2_CONNECTION_ID: ConnectionId =
      ConnectionId.create(XChildEntity::class.java, XChildChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)

    private val connections = listOf<ConnectionId>(
      PARENT1_CONNECTION_ID,
      PARENT2_CONNECTION_ID,
    )

  }

  override val parent1: XParentEntity
    get() = snapshot.extractOneToManyParent(PARENT1_CONNECTION_ID, this)!!

  override val parent2: XChildEntity
    get() = snapshot.extractOneToManyParent(PARENT2_CONNECTION_ID, this)!!

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: XChildChildEntityData?) : ModifiableWorkspaceEntityBase<XChildChildEntity, XChildChildEntityData>(result),
                                                           XChildChildEntity.Builder {
    internal constructor() : this(XChildChildEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity XChildChildEntity is already created in a different builder")
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
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(PARENT1_CONNECTION_ID, this) == null) {
          error("Field XChildChildEntity#parent1 should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT1_CONNECTION_ID)] == null) {
          error("Field XChildChildEntity#parent1 should be initialized")
        }
      }
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(PARENT2_CONNECTION_ID, this) == null) {
          error("Field XChildChildEntity#parent2 should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT2_CONNECTION_ID)] == null) {
          error("Field XChildChildEntity#parent2 should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as XChildChildEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var parent1: XParentEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENT1_CONNECTION_ID, this) as? XParentEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, PARENT1_CONNECTION_ID)]!! as XParentEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, PARENT1_CONNECTION_ID)]!! as XParentEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENT1_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENT1_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(PARENT1_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENT1_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENT1_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENT1_CONNECTION_ID)] = value
        }
        changedProperty.add("parent1")
      }

    override var parent2: XChildEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENT2_CONNECTION_ID, this) as? XChildEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, PARENT2_CONNECTION_ID)]!! as XChildEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, PARENT2_CONNECTION_ID)]!! as XChildEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENT2_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENT2_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(PARENT2_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENT2_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENT2_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENT2_CONNECTION_ID)] = value
        }
        changedProperty.add("parent2")
      }

    override fun getEntityClass(): Class<XChildChildEntity> = XChildChildEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class XChildChildEntityData : WorkspaceEntityData<XChildChildEntity>() {


  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<XChildChildEntity> {
    val modifiable = XChildChildEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): XChildChildEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = XChildChildEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return XChildChildEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return XChildChildEntity(entitySource) {
      parents.filterIsInstance<XParentEntity.Builder>().singleOrNull()?.let { this.parent1 = it }
      parents.filterIsInstance<XChildEntity.Builder>().singleOrNull()?.let { this.parent2 = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(XParentEntity::class.java)
    res.add(XChildEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as XChildChildEntityData

    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as XChildChildEntityData

    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    return result
  }
}
