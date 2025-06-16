// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToOneChild
import com.intellij.platform.workspace.storage.impl.updateOneToOneChildOfParent
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildAlsoWithPidEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildForParentWithPidEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OoParentEntityId
import com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithPidEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class OoParentWithPidEntityImpl(private val dataSource: OoParentWithPidEntityData) : OoParentWithPidEntity,
                                                                                              WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val CHILDONE_CONNECTION_ID: ConnectionId = ConnectionId.create(
      OoParentWithPidEntity::class.java, OoChildForParentWithPidEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false
    )
    internal val CHILDTHREE_CONNECTION_ID: ConnectionId = ConnectionId.create(
      OoParentWithPidEntity::class.java, OoChildAlsoWithPidEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false
    )

    private val connections = listOf<ConnectionId>(
      CHILDONE_CONNECTION_ID,
      CHILDTHREE_CONNECTION_ID,
    )

  }

  override val symbolicId: OoParentEntityId = super.symbolicId

  override val parentProperty: String
    get() {
      readField("parentProperty")
      return dataSource.parentProperty
    }

  override val childOne: OoChildForParentWithPidEntity?
    get() = snapshot.extractOneToOneChild(CHILDONE_CONNECTION_ID, this)

  override val childThree: OoChildAlsoWithPidEntity?
    get() = snapshot.extractOneToOneChild(CHILDTHREE_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: OoParentWithPidEntityData?) :
    ModifiableWorkspaceEntityBase<OoParentWithPidEntity, OoParentWithPidEntityData>(result), OoParentWithPidEntity.Builder {
    internal constructor() : this(OoParentWithPidEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity OoParentWithPidEntity is already created in a different builder")
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
      if (!getEntityData().isParentPropertyInitialized()) {
        error("Field OoParentWithPidEntity#parentProperty should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as OoParentWithPidEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.parentProperty != dataSource.parentProperty) this.parentProperty = dataSource.parentProperty
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var parentProperty: String
      get() = getEntityData().parentProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).parentProperty = value
        changedProperty.add("parentProperty")
      }

    override var childOne: OoChildForParentWithPidEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(
            CHILDONE_CONNECTION_ID, this
          ) as? OoChildForParentWithPidEntity.Builder)
          ?: (this.entityLinks[EntityLink(true, CHILDONE_CONNECTION_ID)] as? OoChildForParentWithPidEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(true, CHILDONE_CONNECTION_ID)] as? OoChildForParentWithPidEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILDONE_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(CHILDONE_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILDONE_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, CHILDONE_CONNECTION_ID)] = value
        }
        changedProperty.add("childOne")
      }

    override var childThree: OoChildAlsoWithPidEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(
            CHILDTHREE_CONNECTION_ID, this
          ) as? OoChildAlsoWithPidEntity.Builder)
          ?: (this.entityLinks[EntityLink(true, CHILDTHREE_CONNECTION_ID)] as? OoChildAlsoWithPidEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(true, CHILDTHREE_CONNECTION_ID)] as? OoChildAlsoWithPidEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILDTHREE_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(CHILDTHREE_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILDTHREE_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, CHILDTHREE_CONNECTION_ID)] = value
        }
        changedProperty.add("childThree")
      }

    override fun getEntityClass(): Class<OoParentWithPidEntity> = OoParentWithPidEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class OoParentWithPidEntityData : WorkspaceEntityData<OoParentWithPidEntity>() {
  lateinit var parentProperty: String

  internal fun isParentPropertyInitialized(): Boolean = ::parentProperty.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<OoParentWithPidEntity> {
    val modifiable = OoParentWithPidEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): OoParentWithPidEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = OoParentWithPidEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithPidEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return OoParentWithPidEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return OoParentWithPidEntity(parentProperty, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as OoParentWithPidEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.parentProperty != other.parentProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as OoParentWithPidEntityData

    if (this.parentProperty != other.parentProperty) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + parentProperty.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + parentProperty.hashCode()
    return result
  }
}
