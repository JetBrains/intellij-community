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
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentLeafEntity
import com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentLeafEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentRootEntity
import com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentRootEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentSymbolicId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class TreeMultiparentRootEntityImpl(private val dataSource: TreeMultiparentRootEntityData) : TreeMultiparentRootEntity,
                                                                                                      WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(TreeMultiparentRootEntity::class.java,
                                                                            TreeMultiparentLeafEntity::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                            true)
    private val connections = listOf<ConnectionId>(CHILDREN_CONNECTION_ID)
  }

  override val symbolicId: TreeMultiparentSymbolicId = super.symbolicId

  override val data: String
    get() {
      readField("data")
      return dataSource.data
    }
  override val children: List<TreeMultiparentLeafEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<TreeMultiparentLeafEntity>)?.toList()
            ?: error("Children list children not found for TreeMultiparentRootEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: TreeMultiparentRootEntityData?) :
    ModifiableWorkspaceEntityBase<TreeMultiparentRootEntity, TreeMultiparentRootEntityData>(result), TreeMultiparentRootEntityBuilder {
    internal constructor() : this(TreeMultiparentRootEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isDataInitialized()) {
        error("Field TreeMultiparentRootEntity#data should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field TreeMultiparentRootEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field TreeMultiparentRootEntity#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as TreeMultiparentRootEntity
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

    // List of non-abstract referenced types
    override var children: List<TreeMultiparentLeafEntityBuilder>
      get() {
// Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @Suppress("UNCHECKED_CAST")
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this)
            .toList() as List<TreeMultiparentLeafEntityBuilder>) + (this.entityLinks[EntityLink(true,
                                                                                                CHILDREN_CONNECTION_ID)] as? List<TreeMultiparentLeafEntityBuilder>
                                                                    ?: emptyList())
        }
        else {
          @Suppress("UNCHECKED_CAST")
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<TreeMultiparentLeafEntityBuilder> ?: emptyList()
        }
      }
      set(value) {
// Setter of the list of non-abstract referenced types
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
// Backref setup before adding to store
              item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
              @Suppress("UNCHECKED_CAST")
              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.instrumentation.replaceChildren(CHILDREN_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
            }
          }
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] = value
        }
        changedProperty.add("children")
      }

    override fun getEntityClass(): Class<TreeMultiparentRootEntity> = TreeMultiparentRootEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class TreeMultiparentRootEntityData : WorkspaceEntityData<TreeMultiparentRootEntity>() {
  lateinit var data: String
  internal fun isDataInitialized(): Boolean = ::data.isInitialized
  override fun newInstance(): TreeMultiparentRootEntity = TreeMultiparentRootEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<TreeMultiparentRootEntity, *> =
    TreeMultiparentRootEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentRootEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return TreeMultiparentRootEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return TreeMultiparentRootEntity(data, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as TreeMultiparentRootEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as TreeMultiparentRootEntityData
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
