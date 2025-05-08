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
import com.intellij.platform.workspace.storage.testEntities.entities.XChildWithOptionalParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class XChildWithOptionalParentEntityImpl(private val dataSource: XChildWithOptionalParentEntityData) :
  XChildWithOptionalParentEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val OPTIONALPARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(
      XParentEntity::class.java, XChildWithOptionalParentEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true
    )

    private val connections = listOf<ConnectionId>(
      OPTIONALPARENT_CONNECTION_ID,
    )

  }

  override val childProperty: String
    get() {
      readField("childProperty")
      return dataSource.childProperty
    }

  override val optionalParent: XParentEntity?
    get() = snapshot.extractOneToManyParent(OPTIONALPARENT_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: XChildWithOptionalParentEntityData?) :
    ModifiableWorkspaceEntityBase<XChildWithOptionalParentEntity, XChildWithOptionalParentEntityData>(result),
    XChildWithOptionalParentEntity.Builder {
    internal constructor() : this(XChildWithOptionalParentEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity XChildWithOptionalParentEntity is already created in a different builder")
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
      if (!getEntityData().isChildPropertyInitialized()) {
        error("Field XChildWithOptionalParentEntity#childProperty should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as XChildWithOptionalParentEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.childProperty != dataSource.childProperty) this.childProperty = dataSource.childProperty
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var childProperty: String
      get() = getEntityData().childProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).childProperty = value
        changedProperty.add("childProperty")
      }

    override var optionalParent: XParentEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(OPTIONALPARENT_CONNECTION_ID, this) as? XParentEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, OPTIONALPARENT_CONNECTION_ID)] as? XParentEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, OPTIONALPARENT_CONNECTION_ID)] as? XParentEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, OPTIONALPARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, OPTIONALPARENT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(OPTIONALPARENT_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, OPTIONALPARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, OPTIONALPARENT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, OPTIONALPARENT_CONNECTION_ID)] = value
        }
        changedProperty.add("optionalParent")
      }

    override fun getEntityClass(): Class<XChildWithOptionalParentEntity> = XChildWithOptionalParentEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class XChildWithOptionalParentEntityData : WorkspaceEntityData<XChildWithOptionalParentEntity>() {
  lateinit var childProperty: String

  internal fun isChildPropertyInitialized(): Boolean = ::childProperty.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<XChildWithOptionalParentEntity> {
    val modifiable = XChildWithOptionalParentEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): XChildWithOptionalParentEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = XChildWithOptionalParentEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.XChildWithOptionalParentEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return XChildWithOptionalParentEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return XChildWithOptionalParentEntity(childProperty, entitySource) {
      this.optionalParent = parents.filterIsInstance<XParentEntity.Builder>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as XChildWithOptionalParentEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.childProperty != other.childProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as XChildWithOptionalParentEntityData

    if (this.childProperty != other.childProperty) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + childProperty.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + childProperty.hashCode()
    return result
  }
}
