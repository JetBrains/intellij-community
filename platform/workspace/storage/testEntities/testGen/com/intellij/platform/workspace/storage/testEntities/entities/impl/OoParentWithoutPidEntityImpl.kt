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
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildWithPidEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildWithPidEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithoutPidEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithoutPidEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class OoParentWithoutPidEntityImpl(private val dataSource: OoParentWithoutPidEntityData) : OoParentWithoutPidEntity,
                                                                                                    WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILDONE_CONNECTION_ID: ConnectionId = ConnectionId.create(OoParentWithoutPidEntity::class.java,
                                                                            OoChildWithPidEntity::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                            false)
    private val connections = listOf<ConnectionId>(CHILDONE_CONNECTION_ID)
  }

  override val parentProperty: String
    get() {
      readField("parentProperty")
      return dataSource.parentProperty
    }
  override val childOne: OoChildWithPidEntity?
    get() = snapshot.instrumentation.getOneChild(CHILDONE_CONNECTION_ID, this) as? OoChildWithPidEntity
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: OoParentWithoutPidEntityData?) :
    ModifiableWorkspaceEntityBase<OoParentWithoutPidEntity, OoParentWithoutPidEntityData>(result), OoParentWithoutPidEntityBuilder {
    internal constructor() : this(OoParentWithoutPidEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isParentPropertyInitialized()) {
        error("Field OoParentWithoutPidEntity#parentProperty should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as OoParentWithoutPidEntity
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
    override var childOne: OoChildWithPidEntityBuilder?
      get() = getChild(CHILDONE_CONNECTION_ID) as? OoChildWithPidEntityBuilder?
      set(value) {
        changeChild(value, CHILDONE_CONNECTION_ID)
        changedProperty.add("childOne")
      }

    override fun getEntityClass(): Class<OoParentWithoutPidEntity> = OoParentWithoutPidEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class OoParentWithoutPidEntityData : WorkspaceEntityData<OoParentWithoutPidEntity>() {
  lateinit var parentProperty: String
  internal fun isParentPropertyInitialized(): Boolean = ::parentProperty.isInitialized
  override fun newInstance(): OoParentWithoutPidEntity = OoParentWithoutPidEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<OoParentWithoutPidEntity, *> = OoParentWithoutPidEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithoutPidEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return OoParentWithoutPidEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return OoParentWithoutPidEntity(parentProperty, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as OoParentWithoutPidEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.parentProperty != other.parentProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as OoParentWithoutPidEntityData
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
