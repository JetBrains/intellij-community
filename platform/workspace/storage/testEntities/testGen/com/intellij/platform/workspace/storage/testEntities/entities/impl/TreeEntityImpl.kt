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
import com.intellij.platform.workspace.storage.testEntities.entities.TreeEntity
import com.intellij.platform.workspace.storage.testEntities.entities.TreeEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class TreeEntityImpl(private val dataSource: TreeEntityData) : TreeEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId =
      ConnectionId.create(TreeEntity::class.java, TreeEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId =
      ConnectionId.create(TreeEntity::class.java, TreeEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
    private val connections = listOf<ConnectionId>(CHILDREN_CONNECTION_ID, PARENTENTITY_CONNECTION_ID)
  }

  override val data: String
    get() {
      readField("data")
      return dataSource.data
    }
  override val children: List<TreeEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<TreeEntity>)?.toList()
            ?: error("Children list children not found for TreeEntity")
  override val parentEntity: TreeEntity?
    get() = snapshot.instrumentation.getParent(PARENTENTITY_CONNECTION_ID, this) as? TreeEntity
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: TreeEntityData?) : ModifiableWorkspaceEntityBase<TreeEntity, TreeEntityData>(result), TreeEntityBuilder {
    internal constructor() : this(TreeEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isDataInitialized()) {
        error("Field TreeEntity#data should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field TreeEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field TreeEntity#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as TreeEntity
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
    override var data: String
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData(true).data = value
        changedProperty.add("data")
      }
    override var children: List<TreeEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CHILDREN_CONNECTION_ID) as List<TreeEntityBuilder>
      set(value) {
        changeChildren(value, CHILDREN_CONNECTION_ID)
        changedProperty.add("children")
      }
    override var parentEntity: TreeEntityBuilder?
      get() = getParent(PARENTENTITY_CONNECTION_ID) as? TreeEntityBuilder? ?: error("parentEntity is null for TreeEntity")
      set(value) {
        changeParentOfMany(value, PARENTENTITY_CONNECTION_ID)
        changedProperty.add("parentEntity")
      }

    override fun getEntityClass(): Class<TreeEntity> = TreeEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class TreeEntityData : WorkspaceEntityData<TreeEntity>() {
  lateinit var data: String
  internal fun isDataInitialized(): Boolean = ::data.isInitialized
  override fun newInstance(): TreeEntity = TreeEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<TreeEntity, *> = TreeEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.TreeEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return TreeEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return TreeEntity(data, entitySource) {
      this.parentEntity = parents.filterIsInstance<TreeEntityBuilder>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as TreeEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as TreeEntityData
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
