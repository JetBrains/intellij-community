// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToAbstractManyChildren
import com.intellij.platform.workspace.storage.impl.extractOneToAbstractManyParent
import com.intellij.platform.workspace.storage.impl.extractOneToAbstractOneParent
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.updateOneToAbstractManyChildrenOfParent
import com.intellij.platform.workspace.storage.impl.updateOneToAbstractManyParentOfChild
import com.intellij.platform.workspace.storage.impl.updateOneToAbstractOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.BaseEntity
import com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntity
import com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionEntity
import com.intellij.platform.workspace.storage.testEntities.entities.RightEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class RightEntityImpl(private val dataSource: RightEntityData) : RightEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId =
      ConnectionId.create(CompositeBaseEntity::class.java, BaseEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, true)
    internal val CHILDREN_CONNECTION_ID: ConnectionId =
      ConnectionId.create(CompositeBaseEntity::class.java, BaseEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, true)
    internal val PARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(
      HeadAbstractionEntity::class.java, CompositeBaseEntity::class.java, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, true
    )

    private val connections = listOf<ConnectionId>(
      PARENTENTITY_CONNECTION_ID,
      CHILDREN_CONNECTION_ID,
      PARENT_CONNECTION_ID,
    )

  }

  override val parentEntity: CompositeBaseEntity?
    get() = snapshot.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this)

  override val children: List<BaseEntity>
    get() = snapshot.extractOneToAbstractManyChildren<BaseEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()

  override val parent: HeadAbstractionEntity?
    get() = snapshot.extractOneToAbstractOneParent(PARENT_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: RightEntityData?) : ModifiableWorkspaceEntityBase<RightEntity, RightEntityData>(result),
                                                     RightEntity.Builder {
    internal constructor() : this(RightEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity RightEntity is already created in a different builder")
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
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field CompositeBaseEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field CompositeBaseEntity#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as RightEntity
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

    override var parentEntity: CompositeBaseEntity.Builder<out CompositeBaseEntity>?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(
            PARENTENTITY_CONNECTION_ID, this
          ) as? CompositeBaseEntity.Builder<out CompositeBaseEntity>)
          ?: (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? CompositeBaseEntity.Builder<out CompositeBaseEntity>)
        }
        else {
          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? CompositeBaseEntity.Builder<out CompositeBaseEntity>
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

    override var children: List<BaseEntity.Builder<out BaseEntity>>
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this)!!
            .toList() as List<BaseEntity.Builder<out BaseEntity>>) +
          (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<BaseEntity.Builder<out BaseEntity>> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as List<BaseEntity.Builder<out BaseEntity>> ?: emptyList()
        }
      }
      set(value) {
        // Set list of ref types for abstract entities
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
              // Backref setup before adding to store an abstract entity
              if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
                item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable
              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.updateOneToAbstractManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value.asSequence())
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] = value
        }
        changedProperty.add("children")
      }

    override var parent: HeadAbstractionEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENT_CONNECTION_ID, this) as? HeadAbstractionEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] as? HeadAbstractionEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] as? HeadAbstractionEntity.Builder
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
          _diff.updateOneToAbstractOneParentOfChild(PARENT_CONNECTION_ID, this, value)
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

    override fun getEntityClass(): Class<RightEntity> = RightEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class RightEntityData : WorkspaceEntityData<RightEntity>() {


  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<RightEntity> {
    val modifiable = RightEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): RightEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = RightEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.RightEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return RightEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return RightEntity(entitySource) {
      this.parentEntity = parents.filterIsInstance<CompositeBaseEntity.Builder<out CompositeBaseEntity>>().singleOrNull()
      this.parent = parents.filterIsInstance<HeadAbstractionEntity.Builder>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as RightEntityData

    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as RightEntityData

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
