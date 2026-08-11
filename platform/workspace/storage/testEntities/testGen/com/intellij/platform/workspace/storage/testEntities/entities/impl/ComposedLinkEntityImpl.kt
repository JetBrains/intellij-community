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
import com.intellij.platform.workspace.storage.testEntities.entities.ComposedId
import com.intellij.platform.workspace.storage.testEntities.entities.ComposedLinkEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ComposedLinkEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ComposedLinkEntityImpl(private val dataSource: ComposedLinkEntityData) : ComposedLinkEntity,
                                                                                        WorkspaceEntityBase(dataSource) {

  override val link: ComposedId
    get() {
      readField("link")
      return dataSource.link
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: ComposedLinkEntityData?) :
    ModifiableWorkspaceEntityBase<ComposedLinkEntity, ComposedLinkEntityData>(result), ComposedLinkEntityBuilder {
    internal constructor() : this(ComposedLinkEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isLinkInitialized()) {
        error("Field ComposedLinkEntity#link should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ComposedLinkEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.link != dataSource.link) this.link = dataSource.link
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var link: ComposedId
      get() = getEntityData().link
      set(value) {
        checkModificationAllowed()
        getEntityData(true).link = value
        changedProperty.add("link")
      }

    override fun getEntityClass(): Class<ComposedLinkEntity> = ComposedLinkEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ComposedLinkEntityData : WorkspaceEntityData<ComposedLinkEntity>(), SoftLinkable {
  lateinit var link: ComposedId
  internal fun isLinkInitialized(): Boolean = ::link.isInitialized
  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    result.add(link)
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    index.index(this, link)
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    val mutablePreviousSet = HashSet(prev)
    val removedItem_link = mutablePreviousSet.remove(link)
    if (!removedItem_link) {
      index.index(this, link)
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val link_data = if (link == oldLink) {
      changed = true
      newLink as ComposedId
    }
    else {
      null
    }
    if (link_data != null) {
      link = link_data
    }
    return changed
  }

  override fun newInstance(): ComposedLinkEntity = ComposedLinkEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ComposedLinkEntity, *> = ComposedLinkEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.ComposedLinkEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ComposedLinkEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ComposedLinkEntity(link, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ComposedLinkEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.link != other.link) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ComposedLinkEntityData
    if (this.link != other.link) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + link.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + link.hashCode()
    return result
  }
}
