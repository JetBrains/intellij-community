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
import com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNullsMultiple
import com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNullsMultipleBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNullsMultiple
import com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNullsMultipleBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentWithNullsMultipleImpl(private val dataSource: ParentWithNullsMultipleData) : ParentWithNullsMultiple,
                                                                                                  WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentWithNullsMultiple::class.java,
                                                                            ChildWithNullsMultiple::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                            true)
    private val connections = listOf<ConnectionId>(CHILDREN_CONNECTION_ID)
  }

  override val parentData: String
    get() {
      readField("parentData")
      return dataSource.parentData
    }
  override val children: List<ChildWithNullsMultiple>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<ChildWithNullsMultiple>)?.toList()
            ?: error("Children list children not found for ParentWithNullsMultiple")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ParentWithNullsMultipleData?) :
    ModifiableWorkspaceEntityBase<ParentWithNullsMultiple, ParentWithNullsMultipleData>(result), ParentWithNullsMultipleBuilder {
    internal constructor() : this(ParentWithNullsMultipleData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isParentDataInitialized()) {
        error("Field ParentWithNullsMultiple#parentData should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field ParentWithNullsMultiple#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field ParentWithNullsMultiple#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ParentWithNullsMultiple
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.parentData != dataSource.parentData) this.parentData = dataSource.parentData
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var parentData: String
      get() = getEntityData().parentData
      set(value) {
        checkModificationAllowed()
        getEntityData(true).parentData = value
        changedProperty.add("parentData")
      }
    override var children: List<ChildWithNullsMultipleBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CHILDREN_CONNECTION_ID) as List<ChildWithNullsMultipleBuilder>
      set(value) {
        changeChildren(value, CHILDREN_CONNECTION_ID)
        changedProperty.add("children")
      }

    override fun getEntityClass(): Class<ParentWithNullsMultiple> = ParentWithNullsMultiple::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentWithNullsMultipleData : WorkspaceEntityData<ParentWithNullsMultiple>() {
  lateinit var parentData: String
  internal fun isParentDataInitialized(): Boolean = ::parentData.isInitialized
  override fun newInstance(): ParentWithNullsMultiple = ParentWithNullsMultipleImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ParentWithNullsMultiple, *> = ParentWithNullsMultipleImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNullsMultiple") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ParentWithNullsMultiple::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ParentWithNullsMultiple(parentData, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ParentWithNullsMultipleData
    if (this.entitySource != other.entitySource) return false
    if (this.parentData != other.parentData) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ParentWithNullsMultipleData
    if (this.parentData != other.parentData) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + parentData.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + parentData.hashCode()
    return result
  }
}
