// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.PCDId1
import com.intellij.platform.workspace.storage.testEntities.entities.PCDIdExtension
import com.intellij.platform.workspace.storage.testEntities.entities.PcdExtensionChild
import com.intellij.platform.workspace.storage.testEntities.entities.PcdExtensionChildBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.PcdParent1Entity
import com.intellij.platform.workspace.storage.testEntities.entities.PcdParent1EntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class PcdExtensionChildImpl(private val dataSource: PcdExtensionChildData) : PcdExtensionChild, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENT_CONNECTION_ID: ConnectionId =
      ConnectionId.create(PcdParent1Entity::class.java, PcdExtensionChild::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(PARENT_CONNECTION_ID)
  }

  override val symbolicId: PCDIdExtension = PCDIdExtension(dataSource.data, dataSource.parentSymbolicId_Synthetic)

  override val data: Float
    get() {
      readField("data")
      return dataSource.data
    }
  override val parent: PcdParent1Entity
    get() = snapshot.instrumentation.getParent(PARENT_CONNECTION_ID, this) as? PcdParent1Entity
            ?: error("Parent parent not found for PcdExtensionChild")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: PcdExtensionChildData?) : ModifiableWorkspaceEntityBase<PcdExtensionChild, PcdExtensionChildData>(result),
                                                           PcdExtensionChildBuilder {
    internal constructor() : this(PcdExtensionChildData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENT_CONNECTION_ID, this) == null) {
          error("Field PcdExtensionChild#parent should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] == null) {
          error("Field PcdExtensionChild#parent should be initialized")
        }
      }
      if (!getEntityData().isParentSymbolicId_SyntheticInitialized()) {
        error("Field PcdExtensionChild#parent should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as PcdExtensionChild
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
    override var data: Float
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData(true).data = value
        changedProperty.add("data")
      }
    override var parent: PcdParent1EntityBuilder
      get() = getParent(PARENT_CONNECTION_ID) as? PcdParent1EntityBuilder ?: error("parent is null for PcdExtensionChild")
      set(value) {
        changeParentOfMany(value, PARENT_CONNECTION_ID)
        changedProperty.add("parent")
        updateSymbolicId(value, PARENT_CONNECTION_ID)
      }

    override fun getEntityClass(): Class<PcdExtensionChild> = PcdExtensionChild::class.java
    override fun updateSymbolicId(parent: WorkspaceEntityBuilder<*>, connectionId: ConnectionId) {
      if (connectionId == PARENT_CONNECTION_ID) {
        parent as PcdParent1EntityBuilder
        getEntityData(true).parentSymbolicId_Synthetic = PCDId1(parent.name)
        changedProperty.add("parentSymbolicId_Synthetic")
      }
    }
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class PcdExtensionChildData : WorkspaceEntityData<PcdExtensionChild>(), SoftLinkable {
  var data: Float = 0f
  lateinit var parentSymbolicId_Synthetic: PCDId1
  internal fun isParentSymbolicId_SyntheticInitialized(): Boolean = ::parentSymbolicId_Synthetic.isInitialized
  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    result.add(parentSymbolicId_Synthetic)
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    index.index(this, parentSymbolicId_Synthetic)
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    val mutablePreviousSet = HashSet(prev)
    val removedItem_parentSymbolicId_Synthetic = mutablePreviousSet.remove(parentSymbolicId_Synthetic)
    if (!removedItem_parentSymbolicId_Synthetic) {
      index.index(this, parentSymbolicId_Synthetic)
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val parentSymbolicId_Synthetic_data = if (parentSymbolicId_Synthetic == oldLink) {
      changed = true
      newLink as PCDId1
    }
    else {
      null
    }
    if (parentSymbolicId_Synthetic_data != null) {
      parentSymbolicId_Synthetic = parentSymbolicId_Synthetic_data
    }
    return changed
  }

  override fun newInstance(): PcdExtensionChild = PcdExtensionChildImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<PcdExtensionChild, *> = PcdExtensionChildImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.PcdExtensionChild") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return PcdExtensionChild::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return PcdExtensionChild(data, entitySource) {
      parents.filterIsInstance<PcdParent1EntityBuilder>().singleOrNull()?.let { this.parent = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(PcdParent1Entity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as PcdExtensionChildData
    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as PcdExtensionChildData
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
