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
import com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithId
import com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithIdBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.ParentWithId
import com.intellij.platform.workspace.storage.testEntities.entities.ParentWithIdBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentWithIdImpl(private val dataSource: ParentWithIdData) : ParentWithId, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENT_CONNECTION_ID: ConnectionId =
      ConnectionId.create(GrandParentWithId::class.java, ParentWithId::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val CHILDREN_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ParentWithId::class.java, ChildWithId::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(PARENT_CONNECTION_ID, CHILDREN_CONNECTION_ID)
  }

  override val symbolicId: ParentWithId.ParentId = super.symbolicId

  override val myId: String
    get() {
      readField("myId")
      return dataSource.myId
    }
  override val parent: GrandParentWithId
    get() = snapshot.instrumentation.getParent(PARENT_CONNECTION_ID, this) as? GrandParentWithId
            ?: error("Parent parent not found for ParentWithId")
  override val children: List<ChildWithId>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<ChildWithId>)?.toList()
            ?: error("Children list children not found for ParentWithId")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ParentWithIdData?) : ModifiableWorkspaceEntityBase<ParentWithId, ParentWithIdData>(result),
                                                      ParentWithIdBuilder {
    internal constructor() : this(ParentWithIdData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isMyIdInitialized()) {
        error("Field ParentWithId#myId should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENT_CONNECTION_ID, this) == null) {
          error("Field ParentWithId#parent should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] == null) {
          error("Field ParentWithId#parent should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ParentWithId
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
    override var parent: GrandParentWithIdBuilder
      get() = getParent(PARENT_CONNECTION_ID) as? GrandParentWithIdBuilder ?: error("parent is null for ParentWithId")
      set(value) {
        changeParentOfMany(value, PARENT_CONNECTION_ID)
        changedProperty.add("parent")
      }
    override var children: List<ChildWithIdBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CHILDREN_CONNECTION_ID) as List<ChildWithIdBuilder>
      set(value) {
        changeChildren(value, CHILDREN_CONNECTION_ID)
        changedProperty.add("children")
      }

    override fun getEntityClass(): Class<ParentWithId> = ParentWithId::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentWithIdData : WorkspaceEntityData<ParentWithId>() {
  lateinit var myId: String
  internal fun isMyIdInitialized(): Boolean = ::myId.isInitialized
  override fun newInstance(): ParentWithId = ParentWithIdImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ParentWithId, *> = ParentWithIdImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.ParentWithId") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ParentWithId::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ParentWithId(myId, entitySource) {
      parents.filterIsInstance<GrandParentWithIdBuilder>().singleOrNull()?.let { this.parent = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(GrandParentWithId::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ParentWithIdData
    if (this.entitySource != other.entitySource) return false
    if (this.myId != other.myId) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ParentWithIdData
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
