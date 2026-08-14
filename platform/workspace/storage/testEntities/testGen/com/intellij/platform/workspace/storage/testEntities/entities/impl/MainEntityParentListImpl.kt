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
import com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityParentList
import com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityParentListBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.MainEntityParentList
import com.intellij.platform.workspace.storage.testEntities.entities.MainEntityParentListBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class MainEntityParentListImpl(private val dataSource: MainEntityParentListData) : MainEntityParentList,
                                                                                            WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(MainEntityParentList::class.java,
                                                                            AttachedEntityParentList::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                            true)
    private val connections = listOf<ConnectionId>(CHILDREN_CONNECTION_ID)
  }

  override val x: String
    get() {
      readField("x")
      return dataSource.x
    }
  override val children: List<AttachedEntityParentList>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<AttachedEntityParentList>)?.toList()
            ?: error("Children list children not found for MainEntityParentList")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: MainEntityParentListData?) :
    ModifiableWorkspaceEntityBase<MainEntityParentList, MainEntityParentListData>(result), MainEntityParentListBuilder {
    internal constructor() : this(MainEntityParentListData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isXInitialized()) {
        error("Field MainEntityParentList#x should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field MainEntityParentList#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field MainEntityParentList#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as MainEntityParentList
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.x != dataSource.x) this.x = dataSource.x
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var x: String
      get() = getEntityData().x
      set(value) {
        checkModificationAllowed()
        getEntityData(true).x = value
        changedProperty.add("x")
      }
    override var children: List<AttachedEntityParentListBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CHILDREN_CONNECTION_ID) as List<AttachedEntityParentListBuilder>
      set(value) {
        changeChildren(value, CHILDREN_CONNECTION_ID)
        changedProperty.add("children")
      }

    override fun getEntityClass(): Class<MainEntityParentList> = MainEntityParentList::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class MainEntityParentListData : WorkspaceEntityData<MainEntityParentList>() {
  lateinit var x: String
  internal fun isXInitialized(): Boolean = ::x.isInitialized
  override fun newInstance(): MainEntityParentList = MainEntityParentListImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<MainEntityParentList, *> = MainEntityParentListImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.MainEntityParentList") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return MainEntityParentList::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return MainEntityParentList(x, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as MainEntityParentListData
    if (this.entitySource != other.entitySource) return false
    if (this.x != other.x) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as MainEntityParentListData
    if (this.x != other.x) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + x.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + x.hashCode()
    return result
  }
}
