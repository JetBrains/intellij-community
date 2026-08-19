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
import com.intellij.platform.workspace.storage.testEntities.entities.CompositeAbstractEntity
import com.intellij.platform.workspace.storage.testEntities.entities.CompositeAbstractEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.CompositeChildAbstractEntity
import com.intellij.platform.workspace.storage.testEntities.entities.CompositeChildAbstractEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.ParentChainEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ParentChainEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.SimpleAbstractEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SimpleAbstractEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class CompositeChildAbstractEntityImpl(private val dataSource: CompositeChildAbstractEntityData) : CompositeChildAbstractEntity,
                                                                                                            WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENTINLIST_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositeAbstractEntity::class.java,
                                                                                SimpleAbstractEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                true)
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositeAbstractEntity::class.java,
                                                                            SimpleAbstractEntity::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                            true)
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentChainEntity::class.java,
                                                                                CompositeAbstractEntity::class.java,
                                                                                ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                true)
    private val connections = listOf<ConnectionId>(PARENTINLIST_CONNECTION_ID, CHILDREN_CONNECTION_ID, PARENTENTITY_CONNECTION_ID)
  }

  override val parentInList: CompositeAbstractEntity?
    get() = snapshot.instrumentation.getParent(PARENTINLIST_CONNECTION_ID, this) as? CompositeAbstractEntity
  override val children: List<SimpleAbstractEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<SimpleAbstractEntity>)?.toList()
            ?: error("Children list children not found for CompositeAbstractEntity")
  override val parentEntity: ParentChainEntity?
    get() = snapshot.instrumentation.getParent(PARENTENTITY_CONNECTION_ID, this) as? ParentChainEntity
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: CompositeChildAbstractEntityData?) :
    ModifiableWorkspaceEntityBase<CompositeChildAbstractEntity, CompositeChildAbstractEntityData>(result),
    CompositeChildAbstractEntityBuilder {
    internal constructor() : this(CompositeChildAbstractEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field CompositeAbstractEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field CompositeAbstractEntity#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as CompositeChildAbstractEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var parentInList: CompositeAbstractEntityBuilder<out CompositeAbstractEntity>?
      get() = getParent(PARENTINLIST_CONNECTION_ID) as? CompositeAbstractEntityBuilder<out CompositeAbstractEntity>?
              ?: error("parentInList is null for SimpleAbstractEntity")
      set(value) {
        changeParentOfMany(value, PARENTINLIST_CONNECTION_ID)
        changedProperty.add("parentInList")
      }
    override var children: List<SimpleAbstractEntityBuilder<out SimpleAbstractEntity>>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CHILDREN_CONNECTION_ID) as List<SimpleAbstractEntityBuilder<out SimpleAbstractEntity>>
      set(value) {
        changeChildren(value, CHILDREN_CONNECTION_ID)
        changedProperty.add("children")
      }
    override var parentEntity: ParentChainEntityBuilder?
      get() = getParent(PARENTENTITY_CONNECTION_ID) as? ParentChainEntityBuilder?
              ?: error("parentEntity is null for CompositeAbstractEntity")
      set(value) {
        changeParent(value, PARENTENTITY_CONNECTION_ID)
        changedProperty.add("parentEntity")
      }

    override fun getEntityClass(): Class<CompositeChildAbstractEntity> = CompositeChildAbstractEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class CompositeChildAbstractEntityData : WorkspaceEntityData<CompositeChildAbstractEntity>() {
  override fun newInstance(): CompositeChildAbstractEntity = CompositeChildAbstractEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<CompositeChildAbstractEntity, *> =
    CompositeChildAbstractEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.CompositeChildAbstractEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return CompositeChildAbstractEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return CompositeChildAbstractEntity(entitySource) {
      this.parentInList = parents.filterIsInstance<CompositeAbstractEntityBuilder<out CompositeAbstractEntity>>().singleOrNull()
      this.parentEntity = parents.filterIsInstance<ParentChainEntityBuilder>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as CompositeChildAbstractEntityData
    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as CompositeChildAbstractEntityData
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    return result
  }
}
