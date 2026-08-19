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
import com.intellij.platform.workspace.storage.testEntities.entities.EntityWithSoftLinks
import com.intellij.platform.workspace.storage.testEntities.entities.EntityWithSoftLinksBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.SoftLinkReferencedChild
import com.intellij.platform.workspace.storage.testEntities.entities.SoftLinkReferencedChildBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SoftLinkReferencedChildImpl(private val dataSource: SoftLinkReferencedChildData) : SoftLinkReferencedChild,
                                                                                                  WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(EntityWithSoftLinks::class.java,
                                                                                SoftLinkReferencedChild::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                false)
    private val connections = listOf<ConnectionId>(PARENTENTITY_CONNECTION_ID)
  }

  override val parentEntity: EntityWithSoftLinks
    get() = snapshot.instrumentation.getParent(PARENTENTITY_CONNECTION_ID, this) as? EntityWithSoftLinks
            ?: error("Parent parentEntity not found for SoftLinkReferencedChild")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: SoftLinkReferencedChildData?) :
    ModifiableWorkspaceEntityBase<SoftLinkReferencedChild, SoftLinkReferencedChildData>(result), SoftLinkReferencedChildBuilder {
    internal constructor() : this(SoftLinkReferencedChildData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENTENTITY_CONNECTION_ID, this) == null) {
          error("Field SoftLinkReferencedChild#parentEntity should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] == null) {
          error("Field SoftLinkReferencedChild#parentEntity should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SoftLinkReferencedChild
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
    override var parentEntity: EntityWithSoftLinksBuilder
      get() = getParent(PARENTENTITY_CONNECTION_ID) as? EntityWithSoftLinksBuilder
              ?: error("parentEntity is null for SoftLinkReferencedChild")
      set(value) {
        changeParentOfMany(value, PARENTENTITY_CONNECTION_ID)
        changedProperty.add("parentEntity")
      }

    override fun getEntityClass(): Class<SoftLinkReferencedChild> = SoftLinkReferencedChild::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SoftLinkReferencedChildData : WorkspaceEntityData<SoftLinkReferencedChild>() {
  override fun newInstance(): SoftLinkReferencedChild = SoftLinkReferencedChildImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<SoftLinkReferencedChild, *> = SoftLinkReferencedChildImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.SoftLinkReferencedChild") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SoftLinkReferencedChild::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return SoftLinkReferencedChild(entitySource) {
      parents.filterIsInstance<EntityWithSoftLinksBuilder>().singleOrNull()?.let { this.parentEntity = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(EntityWithSoftLinks::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SoftLinkReferencedChildData
    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SoftLinkReferencedChildData
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
