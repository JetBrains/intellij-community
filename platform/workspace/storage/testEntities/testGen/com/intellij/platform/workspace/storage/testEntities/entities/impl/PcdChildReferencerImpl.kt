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
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.PCDIdChild
import com.intellij.platform.workspace.storage.testEntities.entities.PcdChildReferencer
import com.intellij.platform.workspace.storage.testEntities.entities.PcdChildReferencerBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class PcdChildReferencerImpl(private val dataSource: PcdChildReferencerData) : PcdChildReferencer,
                                                                                        WorkspaceEntityBase(dataSource) {

  override val data: String
    get() {
      readField("data")
      return dataSource.data
    }
  override val relatedChildEntity: PCDIdChild
    get() {
      readField("relatedChildEntity")
      return dataSource.relatedChildEntity
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: PcdChildReferencerData?) :
    ModifiableWorkspaceEntityBase<PcdChildReferencer, PcdChildReferencerData>(result), PcdChildReferencerBuilder {
    internal constructor() : this(PcdChildReferencerData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isDataInitialized()) {
        error("Field PcdChildReferencer#data should be initialized")
      }
      if (!getEntityData().isRelatedChildEntityInitialized()) {
        error("Field PcdChildReferencer#relatedChildEntity should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as PcdChildReferencer
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.data != dataSource.data) this.data = dataSource.data
      if (this.relatedChildEntity != dataSource.relatedChildEntity) this.relatedChildEntity = dataSource.relatedChildEntity
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var data: String
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData(true).data = value
        changedProperty.add("data")
      }
    override var relatedChildEntity: PCDIdChild
      get() = getEntityData().relatedChildEntity
      set(value) {
        checkModificationAllowed()
        getEntityData(true).relatedChildEntity = value
        changedProperty.add("relatedChildEntity")
      }

    override fun getEntityClass(): Class<PcdChildReferencer> = PcdChildReferencer::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class PcdChildReferencerData : WorkspaceEntityData<PcdChildReferencer>(), SoftLinkable {
  lateinit var data: String
  lateinit var relatedChildEntity: PCDIdChild
  internal fun isDataInitialized(): Boolean = ::data.isInitialized
  internal fun isRelatedChildEntityInitialized(): Boolean = ::relatedChildEntity.isInitialized
  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    result.add(relatedChildEntity)
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    index.index(this, relatedChildEntity)
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    val mutablePreviousSet = HashSet(prev)
    val removedItem_relatedChildEntity = mutablePreviousSet.remove(relatedChildEntity)
    if (!removedItem_relatedChildEntity) {
      index.index(this, relatedChildEntity)
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val relatedChildEntity_data = if (relatedChildEntity == oldLink) {
      changed = true
      newLink as PCDIdChild
    }
    else {
      null
    }
    if (relatedChildEntity_data != null) {
      relatedChildEntity = relatedChildEntity_data
    }
    return changed
  }

  override fun newInstance(): PcdChildReferencer = PcdChildReferencerImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<PcdChildReferencer, *> = PcdChildReferencerImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.PcdChildReferencer") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return PcdChildReferencer::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return PcdChildReferencer(data, relatedChildEntity, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as PcdChildReferencerData
    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    if (this.relatedChildEntity != other.relatedChildEntity) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as PcdChildReferencerData
    if (this.data != other.data) return false
    if (this.relatedChildEntity != other.relatedChildEntity) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + relatedChildEntity.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + relatedChildEntity.hashCode()
    return result
  }
}
