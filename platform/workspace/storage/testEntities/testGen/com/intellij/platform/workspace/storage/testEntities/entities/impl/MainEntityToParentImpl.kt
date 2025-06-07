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
import com.intellij.platform.workspace.storage.impl.extractOneToOneChild
import com.intellij.platform.workspace.storage.impl.updateOneToOneChildOfParent
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityToNullableParent
import com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityToParent
import com.intellij.platform.workspace.storage.testEntities.entities.MainEntityToParent

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class MainEntityToParentImpl(private val dataSource: MainEntityToParentData) : MainEntityToParent,
                                                                                        WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val CHILD_CONNECTION_ID: ConnectionId =
      ConnectionId.create(MainEntityToParent::class.java, AttachedEntityToParent::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    internal val CHILDNULLABLEPARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(
      MainEntityToParent::class.java, AttachedEntityToNullableParent::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, true
    )

    private val connections = listOf<ConnectionId>(
      CHILD_CONNECTION_ID,
      CHILDNULLABLEPARENT_CONNECTION_ID,
    )

  }

  override val x: String
    get() {
      readField("x")
      return dataSource.x
    }

  override val child: AttachedEntityToParent?
    get() = snapshot.extractOneToOneChild(CHILD_CONNECTION_ID, this)

  override val childNullableParent: AttachedEntityToNullableParent?
    get() = snapshot.extractOneToOneChild(CHILDNULLABLEPARENT_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: MainEntityToParentData?) :
    ModifiableWorkspaceEntityBase<MainEntityToParent, MainEntityToParentData>(result), MainEntityToParent.Builder {
    internal constructor() : this(MainEntityToParentData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity MainEntityToParent is already created in a different builder")
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
      if (!getEntityData().isXInitialized()) {
        error("Field MainEntityToParent#x should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as MainEntityToParent
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.x != dataSource.x) this.x = dataSource.x
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var x: String
      get() = getEntityData().x
      set(value) {
        checkModificationAllowed()
        getEntityData(true).x = value
        changedProperty.add("x")
      }

    override var child: AttachedEntityToParent.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(CHILD_CONNECTION_ID, this) as? AttachedEntityToParent.Builder)
          ?: (this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] as? AttachedEntityToParent.Builder)
        }
        else {
          this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] as? AttachedEntityToParent.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(CHILD_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] = value
        }
        changedProperty.add("child")
      }

    override var childNullableParent: AttachedEntityToNullableParent.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(
            CHILDNULLABLEPARENT_CONNECTION_ID, this
          ) as? AttachedEntityToNullableParent.Builder)
          ?: (this.entityLinks[EntityLink(true, CHILDNULLABLEPARENT_CONNECTION_ID)] as? AttachedEntityToNullableParent.Builder)
        }
        else {
          this.entityLinks[EntityLink(true, CHILDNULLABLEPARENT_CONNECTION_ID)] as? AttachedEntityToNullableParent.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILDNULLABLEPARENT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(CHILDNULLABLEPARENT_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILDNULLABLEPARENT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, CHILDNULLABLEPARENT_CONNECTION_ID)] = value
        }
        changedProperty.add("childNullableParent")
      }

    override fun getEntityClass(): Class<MainEntityToParent> = MainEntityToParent::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class MainEntityToParentData : WorkspaceEntityData<MainEntityToParent>() {
  lateinit var x: String

  internal fun isXInitialized(): Boolean = ::x.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<MainEntityToParent> {
    val modifiable = MainEntityToParentImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): MainEntityToParent {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = MainEntityToParentImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.MainEntityToParent"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return MainEntityToParent::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return MainEntityToParent(x, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as MainEntityToParentData

    if (this.entitySource != other.entitySource) return false
    if (this.x != other.x) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as MainEntityToParentData

    if (this.x != other.x) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + x.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + x.hashCode()
    return result
  }
}
