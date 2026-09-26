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
import com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.MainEntity
import com.intellij.platform.workspace.storage.testEntities.entities.MainEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class AttachedEntityImpl(private val dataSource: AttachedEntityData) : AttachedEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val REF_CONNECTION_ID: ConnectionId =
      ConnectionId.create(MainEntity::class.java, AttachedEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val connections = listOf<ConnectionId>(REF_CONNECTION_ID)
  }

  override val ref: MainEntity
    get() = snapshot.instrumentation.getParent(REF_CONNECTION_ID, this) as? MainEntity ?: error("Parent ref not found for AttachedEntity")
  override val data: String
    get() {
      readField("data")
      return dataSource.data
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: AttachedEntityData?) : ModifiableWorkspaceEntityBase<AttachedEntity, AttachedEntityData>(result),
                                                        AttachedEntityBuilder {
    internal constructor() : this(AttachedEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(REF_CONNECTION_ID, this) == null) {
          error("Field AttachedEntity#ref should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, REF_CONNECTION_ID)] == null) {
          error("Field AttachedEntity#ref should be initialized")
        }
      }
      if (!getEntityData().isDataInitialized()) {
        error("Field AttachedEntity#data should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as AttachedEntity
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
    override var ref: MainEntityBuilder
      get() = getParent(REF_CONNECTION_ID) as? MainEntityBuilder ?: error("ref is null for AttachedEntity")
      set(value) {
        changeParent(value, REF_CONNECTION_ID)
        changedProperty.add("ref")
      }
    override var data: String
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData(true).data = value
        changedProperty.add("data")
      }

    override fun getEntityClass(): Class<AttachedEntity> = AttachedEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class AttachedEntityData : WorkspaceEntityData<AttachedEntity>() {
  lateinit var data: String
  internal fun isDataInitialized(): Boolean = ::data.isInitialized
  override fun newInstance(): AttachedEntity = AttachedEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<AttachedEntity, *> = AttachedEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return AttachedEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return AttachedEntity(data, entitySource) {
      parents.filterIsInstance<MainEntityBuilder>().singleOrNull()?.let { this.ref = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(MainEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as AttachedEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as AttachedEntityData
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
