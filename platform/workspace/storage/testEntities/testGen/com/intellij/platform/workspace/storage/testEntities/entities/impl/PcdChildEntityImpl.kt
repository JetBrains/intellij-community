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
import com.intellij.platform.workspace.storage.testEntities.entities.PCDId2
import com.intellij.platform.workspace.storage.testEntities.entities.PCDIdChild
import com.intellij.platform.workspace.storage.testEntities.entities.PcdChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.PcdChildEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.PcdParent1Entity
import com.intellij.platform.workspace.storage.testEntities.entities.PcdParent1EntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.PcdParent2Entity
import com.intellij.platform.workspace.storage.testEntities.entities.PcdParent2EntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class PcdChildEntityImpl(private val dataSource: PcdChildEntityData) : PcdChildEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENT1_CONNECTION_ID: ConnectionId =
      ConnectionId.create(PcdParent1Entity::class.java, PcdChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    internal val PARENT2_CONNECTION_ID: ConnectionId =
      ConnectionId.create(PcdParent2Entity::class.java, PcdChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(PARENT1_CONNECTION_ID, PARENT2_CONNECTION_ID)
  }

  override val symbolicId: PCDIdChild =
    PCDIdChild(dataSource.data, dataSource.parent1SymbolicId_Synthetic, dataSource.parent2SymbolicId_Synthetic)

  override val data: Boolean
    get() {
      readField("data")
      return dataSource.data
    }
  override val parent1: PcdParent1Entity
    get() = snapshot.instrumentation.getParent(PARENT1_CONNECTION_ID, this) as? PcdParent1Entity
            ?: error("Parent parent1 not found for PcdChildEntity")
  override val parent2: PcdParent2Entity
    get() = snapshot.instrumentation.getParent(PARENT2_CONNECTION_ID, this) as? PcdParent2Entity
            ?: error("Parent parent2 not found for PcdChildEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: PcdChildEntityData?) : ModifiableWorkspaceEntityBase<PcdChildEntity, PcdChildEntityData>(result),
                                                        PcdChildEntityBuilder {
    internal constructor() : this(PcdChildEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENT1_CONNECTION_ID, this) == null) {
          error("Field PcdChildEntity#parent1 should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT1_CONNECTION_ID)] == null) {
          error("Field PcdChildEntity#parent1 should be initialized")
        }
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENT2_CONNECTION_ID, this) == null) {
          error("Field PcdChildEntity#parent2 should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT2_CONNECTION_ID)] == null) {
          error("Field PcdChildEntity#parent2 should be initialized")
        }
      }
      if (!getEntityData().isParent1SymbolicId_SyntheticInitialized()) {
        error("Field PcdChildEntity#parent1 should be initialized")
      }
      if (!getEntityData().isParent2SymbolicId_SyntheticInitialized()) {
        error("Field PcdChildEntity#parent2 should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as PcdChildEntity
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
    override var data: Boolean
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData(true).data = value
        changedProperty.add("data")
      }
    override var parent1: PcdParent1EntityBuilder
      get() = getParent(PARENT1_CONNECTION_ID) as? PcdParent1EntityBuilder ?: error("parent1 is null for PcdChildEntity")
      set(value) {
        changeParent(value, PARENT1_CONNECTION_ID)
        changedProperty.add("parent1")
        updateSymbolicId(value, PARENT1_CONNECTION_ID)
      }
    override var parent2: PcdParent2EntityBuilder
      get() = getParent(PARENT2_CONNECTION_ID) as? PcdParent2EntityBuilder ?: error("parent2 is null for PcdChildEntity")
      set(value) {
        changeParentOfMany(value, PARENT2_CONNECTION_ID)
        changedProperty.add("parent2")
        updateSymbolicId(value, PARENT2_CONNECTION_ID)
      }

    override fun getEntityClass(): Class<PcdChildEntity> = PcdChildEntity::class.java
    override fun updateSymbolicId(parent: WorkspaceEntityBuilder<*>, connectionId: ConnectionId) {
      if (connectionId == PARENT1_CONNECTION_ID) {
        parent as PcdParent1EntityBuilder
        getEntityData(true).parent1SymbolicId_Synthetic = PCDId1(parent.name)
        changedProperty.add("parent1SymbolicId_Synthetic")
      }
      if (connectionId == PARENT2_CONNECTION_ID) {
        parent as PcdParent2EntityBuilder
        getEntityData(true).parent2SymbolicId_Synthetic = PCDId2(parent.version)
        changedProperty.add("parent2SymbolicId_Synthetic")
      }
    }
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class PcdChildEntityData : WorkspaceEntityData<PcdChildEntity>(), SoftLinkable {
  var data: Boolean = false
  lateinit var parent1SymbolicId_Synthetic: PCDId1
  lateinit var parent2SymbolicId_Synthetic: PCDId2
  internal fun isParent1SymbolicId_SyntheticInitialized(): Boolean = ::parent1SymbolicId_Synthetic.isInitialized
  internal fun isParent2SymbolicId_SyntheticInitialized(): Boolean = ::parent2SymbolicId_Synthetic.isInitialized
  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    result.add(parent1SymbolicId_Synthetic)
    result.add(parent2SymbolicId_Synthetic)
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    index.index(this, parent1SymbolicId_Synthetic)
    index.index(this, parent2SymbolicId_Synthetic)
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    val mutablePreviousSet = HashSet(prev)
    val removedItem_parent1SymbolicId_Synthetic = mutablePreviousSet.remove(parent1SymbolicId_Synthetic)
    if (!removedItem_parent1SymbolicId_Synthetic) {
      index.index(this, parent1SymbolicId_Synthetic)
    }
    val removedItem_parent2SymbolicId_Synthetic = mutablePreviousSet.remove(parent2SymbolicId_Synthetic)
    if (!removedItem_parent2SymbolicId_Synthetic) {
      index.index(this, parent2SymbolicId_Synthetic)
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val parent1SymbolicId_Synthetic_data = if (parent1SymbolicId_Synthetic == oldLink) {
      changed = true
      newLink as PCDId1
    }
    else {
      null
    }
    if (parent1SymbolicId_Synthetic_data != null) {
      parent1SymbolicId_Synthetic = parent1SymbolicId_Synthetic_data
    }
    val parent2SymbolicId_Synthetic_data = if (parent2SymbolicId_Synthetic == oldLink) {
      changed = true
      newLink as PCDId2
    }
    else {
      null
    }
    if (parent2SymbolicId_Synthetic_data != null) {
      parent2SymbolicId_Synthetic = parent2SymbolicId_Synthetic_data
    }
    return changed
  }

  override fun newInstance(): PcdChildEntity = PcdChildEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<PcdChildEntity, *> = PcdChildEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.PcdChildEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return PcdChildEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return PcdChildEntity(data, entitySource) {
      parents.filterIsInstance<PcdParent1EntityBuilder>().singleOrNull()?.let { this.parent1 = it }
      parents.filterIsInstance<PcdParent2EntityBuilder>().singleOrNull()?.let { this.parent2 = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(PcdParent1Entity::class.java)
    res.add(PcdParent2Entity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as PcdChildEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as PcdChildEntityData
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
