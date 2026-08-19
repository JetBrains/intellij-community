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
import com.intellij.platform.workspace.storage.testEntities.entities.ChildMultipleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ChildMultipleEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.ParentMultipleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ParentMultipleEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildMultipleEntityImpl(private val dataSource: ChildMultipleEntityData) : ChildMultipleEntity,
                                                                                          WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ParentMultipleEntity::class.java, ChildMultipleEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(PARENTENTITY_CONNECTION_ID)
  }

  override val childData: String
    get() {
      readField("childData")
      return dataSource.childData
    }
  override val parentEntity: ParentMultipleEntity
    get() = snapshot.instrumentation.getParent(PARENTENTITY_CONNECTION_ID, this) as? ParentMultipleEntity
            ?: error("Parent parentEntity not found for ChildMultipleEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ChildMultipleEntityData?) :
    ModifiableWorkspaceEntityBase<ChildMultipleEntity, ChildMultipleEntityData>(result), ChildMultipleEntityBuilder {
    internal constructor() : this(ChildMultipleEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isChildDataInitialized()) {
        error("Field ChildMultipleEntity#childData should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENTENTITY_CONNECTION_ID, this) == null) {
          error("Field ChildMultipleEntity#parentEntity should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] == null) {
          error("Field ChildMultipleEntity#parentEntity should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ChildMultipleEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.childData != dataSource.childData) this.childData = dataSource.childData
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var childData: String
      get() = getEntityData().childData
      set(value) {
        checkModificationAllowed()
        getEntityData(true).childData = value
        changedProperty.add("childData")
      }
    override var parentEntity: ParentMultipleEntityBuilder
      get() = getParent(PARENTENTITY_CONNECTION_ID) as? ParentMultipleEntityBuilder ?: error("parentEntity is null for ChildMultipleEntity")
      set(value) {
        changeParentOfMany(value, PARENTENTITY_CONNECTION_ID)
        changedProperty.add("parentEntity")
      }

    override fun getEntityClass(): Class<ChildMultipleEntity> = ChildMultipleEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildMultipleEntityData : WorkspaceEntityData<ChildMultipleEntity>() {
  lateinit var childData: String
  internal fun isChildDataInitialized(): Boolean = ::childData.isInitialized
  override fun newInstance(): ChildMultipleEntity = ChildMultipleEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ChildMultipleEntity, *> = ChildMultipleEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.ChildMultipleEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ChildMultipleEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ChildMultipleEntity(childData, entitySource) {
      parents.filterIsInstance<ParentMultipleEntityBuilder>().singleOrNull()?.let { this.parentEntity = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ParentMultipleEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ChildMultipleEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.childData != other.childData) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ChildMultipleEntityData
    if (this.childData != other.childData) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + childData.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + childData.hashCode()
    return result
  }
}
