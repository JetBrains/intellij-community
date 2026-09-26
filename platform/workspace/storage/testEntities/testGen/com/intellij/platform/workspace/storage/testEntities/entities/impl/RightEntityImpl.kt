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
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.BaseEntity
import com.intellij.platform.workspace.storage.testEntities.entities.BaseEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntity
import com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionEntity
import com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.RightEntity
import com.intellij.platform.workspace.storage.testEntities.entities.RightEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class RightEntityImpl(private val dataSource: RightEntityData) : RightEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId =
      ConnectionId.create(CompositeBaseEntity::class.java, BaseEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, true)
    internal val CHILDREN_CONNECTION_ID: ConnectionId =
      ConnectionId.create(CompositeBaseEntity::class.java, BaseEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, true)
    internal val PARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(HeadAbstractionEntity::class.java,
                                                                          CompositeBaseEntity::class.java,
                                                                          ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                          true)
    private val connections = listOf<ConnectionId>(PARENTENTITY_CONNECTION_ID, CHILDREN_CONNECTION_ID, PARENT_CONNECTION_ID)
  }

  override val parentEntity: CompositeBaseEntity?
    get() = snapshot.instrumentation.getParent(PARENTENTITY_CONNECTION_ID, this) as? CompositeBaseEntity
  override val children: List<BaseEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<BaseEntity>)?.toList()
            ?: error("Children list children not found for CompositeBaseEntity")
  override val parent: HeadAbstractionEntity?
    get() = snapshot.instrumentation.getParent(PARENT_CONNECTION_ID, this) as? HeadAbstractionEntity
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: RightEntityData?) : ModifiableWorkspaceEntityBase<RightEntity, RightEntityData>(result),
                                                     RightEntityBuilder {
    internal constructor() : this(RightEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as RightEntity
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
    override var parentEntity: CompositeBaseEntityBuilder<out CompositeBaseEntity>?
      @Suppress("UNCHECKED_CAST")
      get() = getParent(PARENTENTITY_CONNECTION_ID) as? CompositeBaseEntityBuilder<out CompositeBaseEntity>?
              ?: error("parentEntity is null for BaseEntity")
      set(value) {
        changeParentOfMany(value, PARENTENTITY_CONNECTION_ID)
        changedProperty.add("parentEntity")
      }
    override var children: List<BaseEntityBuilder<out BaseEntity>>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CHILDREN_CONNECTION_ID) as List<BaseEntityBuilder<out BaseEntity>>
      set(value) {
        changeChildren(value, CHILDREN_CONNECTION_ID)
        changedProperty.add("children")
      }
    override var parent: HeadAbstractionEntityBuilder?
      get() = getParent(PARENT_CONNECTION_ID) as? HeadAbstractionEntityBuilder? ?: error("parent is null for CompositeBaseEntity")
      set(value) {
        changeParent(value, PARENT_CONNECTION_ID)
        changedProperty.add("parent")
      }

    override fun getEntityClass(): Class<RightEntity> = RightEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class RightEntityData : WorkspaceEntityData<RightEntity>() {
  override fun newInstance(): RightEntity = RightEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<RightEntity, *> = RightEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.RightEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return RightEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return RightEntity(entitySource) {
      this.parentEntity = parents.filterIsInstance<CompositeBaseEntityBuilder<out CompositeBaseEntity>>().singleOrNull()
      this.parent = parents.filterIsInstance<HeadAbstractionEntityBuilder>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as RightEntityData
    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as RightEntityData
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
