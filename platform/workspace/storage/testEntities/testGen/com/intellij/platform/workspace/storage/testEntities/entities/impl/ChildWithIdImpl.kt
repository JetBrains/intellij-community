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
import com.intellij.platform.workspace.storage.testEntities.entities.ChildWithId
import com.intellij.platform.workspace.storage.testEntities.entities.ChildWithIdBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.ParentWithId
import com.intellij.platform.workspace.storage.testEntities.entities.ParentWithIdBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildWithIdImpl(private val dataSource: ChildWithIdData) : ChildWithId, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENT_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ParentWithId::class.java, ChildWithId::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(PARENT_CONNECTION_ID)
  }

  override val symbolicId: ChildWithId.ChildId = super.symbolicId

  override val myId: String
    get() {
      readField("myId")
      return dataSource.myId
    }
  override val parent: ParentWithId
    get() = snapshot.instrumentation.getParent(PARENT_CONNECTION_ID, this) as? ParentWithId
            ?: error("Parent parent not found for ChildWithId")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ChildWithIdData?) : ModifiableWorkspaceEntityBase<ChildWithId, ChildWithIdData>(result),
                                                     ChildWithIdBuilder {
    internal constructor() : this(ChildWithIdData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isMyIdInitialized()) {
        error("Field ChildWithId#myId should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENT_CONNECTION_ID, this) == null) {
          error("Field ChildWithId#parent should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] == null) {
          error("Field ChildWithId#parent should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ChildWithId
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.myId != dataSource.myId) this.myId = dataSource.myId
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var myId: String
      get() = getEntityData().myId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).myId = value
        changedProperty.add("myId")
      }
    override var parent: ParentWithIdBuilder
      get() = getParent(PARENT_CONNECTION_ID) as? ParentWithIdBuilder ?: error("parent is null for ChildWithId")
      set(value) {
        changeParentOfMany(value, PARENT_CONNECTION_ID)
        changedProperty.add("parent")
      }

    override fun getEntityClass(): Class<ChildWithId> = ChildWithId::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildWithIdData : WorkspaceEntityData<ChildWithId>() {
  lateinit var myId: String
  internal fun isMyIdInitialized(): Boolean = ::myId.isInitialized
  override fun newInstance(): ChildWithId = ChildWithIdImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ChildWithId, *> = ChildWithIdImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.ChildWithId") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ChildWithId::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ChildWithId(myId, entitySource) {
      parents.filterIsInstance<ParentWithIdBuilder>().singleOrNull()?.let { this.parent = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ParentWithId::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ChildWithIdData
    if (this.entitySource != other.entitySource) return false
    if (this.myId != other.myId) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ChildWithIdData
    if (this.myId != other.myId) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + myId.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + myId.hashCode()
    return result
  }
}
