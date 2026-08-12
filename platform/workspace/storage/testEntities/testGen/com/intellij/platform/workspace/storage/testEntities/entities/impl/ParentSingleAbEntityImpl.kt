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
import com.intellij.platform.workspace.storage.testEntities.entities.ChildSingleAbstractBaseEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ChildSingleAbstractBaseEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.ParentSingleAbEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ParentSingleAbEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentSingleAbEntityImpl(private val dataSource: ParentSingleAbEntityData) : ParentSingleAbEntity,
                                                                                            WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentSingleAbEntity::class.java,
                                                                         ChildSingleAbstractBaseEntity::class.java,
                                                                         ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                         false)
    private val connections = listOf<ConnectionId>(CHILD_CONNECTION_ID)
  }

  override val child: ChildSingleAbstractBaseEntity?
    get() = snapshot.instrumentation.getOneChild(CHILD_CONNECTION_ID, this) as? ChildSingleAbstractBaseEntity
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ParentSingleAbEntityData?) :
    ModifiableWorkspaceEntityBase<ParentSingleAbEntity, ParentSingleAbEntityData>(result), ParentSingleAbEntityBuilder {
    internal constructor() : this(ParentSingleAbEntityData())

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
      dataSource as ParentSingleAbEntity
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
    override var child: ChildSingleAbstractBaseEntityBuilder<out ChildSingleAbstractBaseEntity>?
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(CHILD_CONNECTION_ID,
                                                                             this) as? ChildSingleAbstractBaseEntityBuilder<out ChildSingleAbstractBaseEntity>)
          ?: (this.entityLinks[EntityLink(true,
                                          CHILD_CONNECTION_ID)] as? ChildSingleAbstractBaseEntityBuilder<out ChildSingleAbstractBaseEntity>)
        }
        else {
          (this.entityLinks[EntityLink(true,
                                       CHILD_CONNECTION_ID)] as? ChildSingleAbstractBaseEntityBuilder<out ChildSingleAbstractBaseEntity>)
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          value.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)] = this
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.replaceChildren(CHILD_CONNECTION_ID, this, listOfNotNull(value))
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)] = this
          }
          this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] = value
        }
        changedProperty.add("child")
      }

    override fun getEntityClass(): Class<ParentSingleAbEntity> = ParentSingleAbEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentSingleAbEntityData : WorkspaceEntityData<ParentSingleAbEntity>() {
  override fun newInstance(): ParentSingleAbEntity = ParentSingleAbEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ParentSingleAbEntity, *> = ParentSingleAbEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.ParentSingleAbEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ParentSingleAbEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ParentSingleAbEntity(entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ParentSingleAbEntityData
    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ParentSingleAbEntityData
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
