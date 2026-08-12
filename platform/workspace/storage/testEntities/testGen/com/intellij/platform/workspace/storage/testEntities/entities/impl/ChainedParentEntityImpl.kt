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
import com.intellij.platform.workspace.storage.testEntities.entities.ChainedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ChainedEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.ChainedParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ChainedParentEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChainedParentEntityImpl(private val dataSource: ChainedParentEntityData) : ChainedParentEntity,
                                                                                          WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILD_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ChainedParentEntity::class.java, ChainedEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
    private val connections = listOf<ConnectionId>(CHILD_CONNECTION_ID)
  }

  override val child: List<ChainedEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILD_CONNECTION_ID, this) as? Sequence<ChainedEntity>)?.toList()
            ?: error("Children list child not found for ChainedParentEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ChainedParentEntityData?) :
    ModifiableWorkspaceEntityBase<ChainedParentEntity, ChainedParentEntityData>(result), ChainedParentEntityBuilder {
    internal constructor() : this(ChainedParentEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(CHILD_CONNECTION_ID, this) == null) {
          error("Field ChainedParentEntity#child should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] == null) {
          error("Field ChainedParentEntity#child should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ChainedParentEntity
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

    // List of non-abstract referenced types
    override var child: List<ChainedEntityBuilder>
      get() {
// Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @Suppress("UNCHECKED_CAST")
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILD_CONNECTION_ID, this)
            .toList() as List<ChainedEntityBuilder>) + (this.entityLinks[EntityLink(true,
                                                                                    CHILD_CONNECTION_ID)] as? List<ChainedEntityBuilder>
                                                        ?: emptyList())
        }
        else {
          @Suppress("UNCHECKED_CAST")
          this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] as? List<ChainedEntityBuilder> ?: emptyList()
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
              item_value.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)] = this
              @Suppress("UNCHECKED_CAST")
              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.instrumentation.replaceChildren(CHILD_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)] = this
            }
          }
          this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] = value
        }
        changedProperty.add("child")
      }

    override fun getEntityClass(): Class<ChainedParentEntity> = ChainedParentEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ChainedParentEntityData : WorkspaceEntityData<ChainedParentEntity>() {
  override fun newInstance(): ChainedParentEntity = ChainedParentEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ChainedParentEntity, *> = ChainedParentEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.ChainedParentEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ChainedParentEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ChainedParentEntity(entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ChainedParentEntityData
    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ChainedParentEntityData
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
