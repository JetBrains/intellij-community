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
import com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityList
import com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityListBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.MainEntityList
import com.intellij.platform.workspace.storage.testEntities.entities.MainEntityListBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class AttachedEntityListImpl(private val dataSource: AttachedEntityListData) : AttachedEntityList,
                                                                                        WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val REF_CONNECTION_ID: ConnectionId =
      ConnectionId.create(MainEntityList::class.java, AttachedEntityList::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
    private val connections = listOf<ConnectionId>(REF_CONNECTION_ID)
  }

  override val ref: MainEntityList?
    get() = snapshot.instrumentation.getParent(REF_CONNECTION_ID, this) as? MainEntityList
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

  internal class Builder(result: AttachedEntityListData?) :
    ModifiableWorkspaceEntityBase<AttachedEntityList, AttachedEntityListData>(result), AttachedEntityListBuilder {
    internal constructor() : this(AttachedEntityListData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isDataInitialized()) {
        error("Field AttachedEntityList#data should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as AttachedEntityList
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
    override var ref: MainEntityListBuilder?
      get() = getParent(REF_CONNECTION_ID) as? MainEntityListBuilder? ?: error("ref is null for AttachedEntityList")
      set(value) {
        changeParentOfMany(value, REF_CONNECTION_ID)
        changedProperty.add("ref")
      }
    override var data: String
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData(true).data = value
        changedProperty.add("data")
      }

    override fun getEntityClass(): Class<AttachedEntityList> = AttachedEntityList::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class AttachedEntityListData : WorkspaceEntityData<AttachedEntityList>() {
  lateinit var data: String
  internal fun isDataInitialized(): Boolean = ::data.isInitialized
  override fun newInstance(): AttachedEntityList = AttachedEntityListImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<AttachedEntityList, *> = AttachedEntityListImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityList") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return AttachedEntityList::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return AttachedEntityList(data, entitySource) {
      this.ref = parents.filterIsInstance<MainEntityListBuilder>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as AttachedEntityListData
    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as AttachedEntityListData
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
