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

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class TreeMultiparentLeafEntityImpl(private val dataSource: TreeMultiparentLeafEntityData) : TreeMultiparentLeafEntity,
                                                                                                      WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val MAINPARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(TreeMultiparentRootEntity::class.java,
                                                                              TreeMultiparentLeafEntity::class.java,
                                                                              ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                              true)
    internal val LEAFPARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(TreeMultiparentLeafEntity::class.java,
                                                                              TreeMultiparentLeafEntity::class.java,
                                                                              ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                              true)
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(TreeMultiparentLeafEntity::class.java,
                                                                            TreeMultiparentLeafEntity::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                            true)
    private val connections = listOf<ConnectionId>(MAINPARENT_CONNECTION_ID, LEAFPARENT_CONNECTION_ID, CHILDREN_CONNECTION_ID)
  }

  override val data: String
    get() {
      readField("data")
      return dataSource.data
    }
  override val mainParent: TreeMultiparentRootEntity?
    get() = snapshot.instrumentation.getParent(MAINPARENT_CONNECTION_ID, this) as? TreeMultiparentRootEntity
  override val leafParent: TreeMultiparentLeafEntity?
    get() = snapshot.instrumentation.getParent(LEAFPARENT_CONNECTION_ID, this) as? TreeMultiparentLeafEntity
  override val children: List<TreeMultiparentLeafEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<TreeMultiparentLeafEntity>)?.toList()
            ?: error("Children list children not found for TreeMultiparentLeafEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: TreeMultiparentLeafEntityData?) :
    ModifiableWorkspaceEntityBase<TreeMultiparentLeafEntity, TreeMultiparentLeafEntityData>(result), TreeMultiparentLeafEntityBuilder {
    internal constructor() : this(TreeMultiparentLeafEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isDataInitialized()) {
        error("Field TreeMultiparentLeafEntity#data should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field TreeMultiparentLeafEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field TreeMultiparentLeafEntity#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as TreeMultiparentLeafEntity
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
    override var mainParent: TreeMultiparentRootEntityBuilder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(MAINPARENT_CONNECTION_ID,
                                                                           this) as? TreeMultiparentRootEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, MAINPARENT_CONNECTION_ID)] as? TreeMultiparentRootEntityBuilder)
        }
        else {
          (this.entityLinks[EntityLink(false, MAINPARENT_CONNECTION_ID)] as? TreeMultiparentRootEntityBuilder)
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
// Setting backref of the list
          @Suppress("UNCHECKED_CAST")
          val data = (value.entityLinks[EntityLink(true, MAINPARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
          value.entityLinks[EntityLink(true, MAINPARENT_CONNECTION_ID)] = data
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(MAINPARENT_CONNECTION_ID, value, this)
        }
        else {
// Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val data = (value.entityLinks[EntityLink(true, MAINPARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, MAINPARENT_CONNECTION_ID)] = data
          }
          this.entityLinks[EntityLink(false, MAINPARENT_CONNECTION_ID)] = value
        }
        changedProperty.add("mainParent")
      }
    override var leafParent: TreeMultiparentLeafEntityBuilder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(LEAFPARENT_CONNECTION_ID,
                                                                           this) as? TreeMultiparentLeafEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, LEAFPARENT_CONNECTION_ID)] as? TreeMultiparentLeafEntityBuilder)
        }
        else {
          (this.entityLinks[EntityLink(false, LEAFPARENT_CONNECTION_ID)] as? TreeMultiparentLeafEntityBuilder)
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
// Setting backref of the list
          @Suppress("UNCHECKED_CAST")
          val data = (value.entityLinks[EntityLink(true, LEAFPARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
          value.entityLinks[EntityLink(true, LEAFPARENT_CONNECTION_ID)] = data
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(LEAFPARENT_CONNECTION_ID, value, this)
        }
        else {
// Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val data = (value.entityLinks[EntityLink(true, LEAFPARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, LEAFPARENT_CONNECTION_ID)] = data
          }
          this.entityLinks[EntityLink(false, LEAFPARENT_CONNECTION_ID)] = value
        }
        changedProperty.add("leafParent")
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

    override fun getEntityClass(): Class<TreeMultiparentLeafEntity> = TreeMultiparentLeafEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class TreeMultiparentLeafEntityData : WorkspaceEntityData<TreeMultiparentLeafEntity>() {
  lateinit var data: String
  internal fun isDataInitialized(): Boolean = ::data.isInitialized
  override fun newInstance(): TreeMultiparentLeafEntity = TreeMultiparentLeafEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<TreeMultiparentLeafEntity, *> =
    TreeMultiparentLeafEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentLeafEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return TreeMultiparentLeafEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return TreeMultiparentLeafEntity(data, entitySource) {
      this.mainParent = parents.filterIsInstance<TreeMultiparentRootEntityBuilder>().singleOrNull()
      this.leafParent = parents.filterIsInstance<TreeMultiparentLeafEntityBuilder>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as TreeMultiparentLeafEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as TreeMultiparentLeafEntityData
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
