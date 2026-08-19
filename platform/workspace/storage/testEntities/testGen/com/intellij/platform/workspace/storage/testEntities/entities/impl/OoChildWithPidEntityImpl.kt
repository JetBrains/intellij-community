// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildEntityId
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildWithPidEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildWithPidEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithoutPidEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithoutPidEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class OoChildWithPidEntityImpl(private val dataSource: OoChildWithPidEntityData) : OoChildWithPidEntity,
                                                                                            WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(OoParentWithoutPidEntity::class.java,
                                                                                OoChildWithPidEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                false)
    private val connections = listOf<ConnectionId>(PARENTENTITY_CONNECTION_ID)
  }

  override val symbolicId: OoChildEntityId = super.symbolicId

  override val childProperty: String
    get() {
      readField("childProperty")
      return dataSource.childProperty
    }
  override val parentEntity: OoParentWithoutPidEntity
    get() = snapshot.instrumentation.getParent(PARENTENTITY_CONNECTION_ID, this) as? OoParentWithoutPidEntity
            ?: error("Parent parentEntity not found for OoChildWithPidEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: OoChildWithPidEntityData?) :
    ModifiableWorkspaceEntityBase<OoChildWithPidEntity, OoChildWithPidEntityData>(result), OoChildWithPidEntityBuilder {
    internal constructor() : this(OoChildWithPidEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isChildPropertyInitialized()) {
        error("Field OoChildWithPidEntity#childProperty should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENTENTITY_CONNECTION_ID, this) == null) {
          error("Field OoChildWithPidEntity#parentEntity should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] == null) {
          error("Field OoChildWithPidEntity#parentEntity should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as OoChildWithPidEntity
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
    override var parentEntity: OoParentWithoutPidEntityBuilder
      get() = getParent(PARENTENTITY_CONNECTION_ID) as? OoParentWithoutPidEntityBuilder
              ?: error("parentEntity is null for OoChildWithPidEntity")
      set(value) {
        changeParent(value, PARENTENTITY_CONNECTION_ID)
        changedProperty.add("parentEntity")
      }

    override fun getEntityClass(): Class<OoChildWithPidEntity> = OoChildWithPidEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class OoChildWithPidEntityData : WorkspaceEntityData<OoChildWithPidEntity>() {
  lateinit var childProperty: String
  internal fun isChildPropertyInitialized(): Boolean = ::childProperty.isInitialized
  override fun newInstance(): OoChildWithPidEntity = OoChildWithPidEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<OoChildWithPidEntity, *> = OoChildWithPidEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.OoChildWithPidEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return OoChildWithPidEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return OoChildWithPidEntity(childProperty, entitySource) {
      parents.filterIsInstance<OoParentWithoutPidEntityBuilder>().singleOrNull()?.let { this.parentEntity = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(OoParentWithoutPidEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as OoChildWithPidEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.childProperty != other.childProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as OoChildWithPidEntityData
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
