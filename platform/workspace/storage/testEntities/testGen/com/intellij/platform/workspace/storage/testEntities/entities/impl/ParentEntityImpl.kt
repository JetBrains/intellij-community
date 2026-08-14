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
import com.intellij.platform.workspace.storage.testEntities.entities.ChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ChildEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.ParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ParentEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentEntityImpl(private val dataSource: ParentEntityData) : ParentEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILD_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ParentEntity::class.java, ChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val connections = listOf<ConnectionId>(CHILD_CONNECTION_ID)
  }

  override val parentData: String
    get() {
      readField("parentData")
      return dataSource.parentData
    }
  override val child: ChildEntity?
    get() = snapshot.instrumentation.getOneChild(CHILD_CONNECTION_ID, this) as? ChildEntity
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ParentEntityData?) : ModifiableWorkspaceEntityBase<ParentEntity, ParentEntityData>(result),
                                                      ParentEntityBuilder {
    internal constructor() : this(ParentEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isParentDataInitialized()) {
        error("Field ParentEntity#parentData should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ParentEntity
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
    override var child: ChildEntityBuilder?
      get() = getChild(CHILD_CONNECTION_ID) as? ChildEntityBuilder?
      set(value) {
        changeChild(value, CHILD_CONNECTION_ID)
        changedProperty.add("child")
      }

    override fun getEntityClass(): Class<ParentEntity> = ParentEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentEntityData : WorkspaceEntityData<ParentEntity>() {
  lateinit var parentData: String
  internal fun isParentDataInitialized(): Boolean = ::parentData.isInitialized
  override fun newInstance(): ParentEntity = ParentEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ParentEntity, *> = ParentEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.ParentEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ParentEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ParentEntity(parentData, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ParentEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.parentData != other.parentData) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ParentEntityData
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
