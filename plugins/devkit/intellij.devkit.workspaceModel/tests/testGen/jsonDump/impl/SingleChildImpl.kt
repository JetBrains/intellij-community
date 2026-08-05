// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.devkit.workspaceModel.jsonDump.impl

import com.intellij.devkit.workspaceModel.jsonDump.BaseTestEntity
import com.intellij.devkit.workspaceModel.jsonDump.BaseTestEntityBuilder
import com.intellij.devkit.workspaceModel.jsonDump.SingleChild
import com.intellij.devkit.workspaceModel.jsonDump.SingleChildBuilder
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SingleChildImpl(private val dataSource: SingleChildData) : SingleChild, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val PARENT_CONNECTION_ID: ConnectionId =
      ConnectionId.create(BaseTestEntity::class.java, SingleChild::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val connections = listOf<ConnectionId>(PARENT_CONNECTION_ID)

  }

  override val someData: String
    get() {
      readField("someData")
      return dataSource.someData
    }
  override val parent: BaseTestEntity
    get() = snapshot.instrumentation.getParent(PARENT_CONNECTION_ID, this) as? BaseTestEntity
            ?: error("Parent parent not found for SingleChild")

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: SingleChildData?) : ModifiableWorkspaceEntityBase<SingleChild, SingleChildData>(result),
                                                     SingleChildBuilder {
    internal constructor() : this(SingleChildData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SingleChild is already created in a different builder")
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
      if (!getEntityData().isSomeDataInitialized()) {
        error("Field SingleChild#someData should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENT_CONNECTION_ID, this) == null) {
          error("Field SingleChild#parent should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] == null) {
          error("Field SingleChild#parent should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SingleChild
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.someData != dataSource.someData) this.someData = dataSource.someData
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }
    override var someData: String
      get() = getEntityData().someData
      set(value) {
        checkModificationAllowed()
        getEntityData(true).someData = value
        changedProperty.add("someData")
      }
    override var parent: BaseTestEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENT_CONNECTION_ID, this) as? BaseTestEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] as? BaseTestEntityBuilder)
          ?: error("parent is null for SingleChild")
        }
        else {
          (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] as? BaseTestEntityBuilder) ?: error("parent is null for SingleChild")
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
          _diff.instrumentation.addChild(PARENT_CONNECTION_ID, value, this)
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

    override fun getEntityClass(): Class<SingleChild> = SingleChild::class.java
  }

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SingleChildData : WorkspaceEntityData<SingleChild>() {
  lateinit var someData: String

  internal fun isSomeDataInitialized(): Boolean = ::someData.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<SingleChild> {
    val modifiable = SingleChildImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorageInstrumentation): SingleChild {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = SingleChildImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.devkit.workspaceModel.jsonDump.SingleChild") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SingleChild::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return SingleChild(someData, entitySource) {
      parents.filterIsInstance<BaseTestEntityBuilder>().singleOrNull()?.let { this.parent = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(BaseTestEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SingleChildData
    if (this.entitySource != other.entitySource) return false
    if (this.someData != other.someData) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SingleChildData
    if (this.someData != other.someData) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + someData.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + someData.hashCode()
    return result
  }
}
