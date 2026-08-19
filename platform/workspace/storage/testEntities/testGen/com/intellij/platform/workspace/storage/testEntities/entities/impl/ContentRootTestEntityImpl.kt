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
import com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.SourceRootTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SourceRootTestEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.SourceRootTestOrderEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SourceRootTestOrderEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ContentRootTestEntityImpl(private val dataSource: ContentRootTestEntityData) : ContentRootTestEntity,
                                                                                              WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleTestEntity::class.java, ContentRootTestEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val SOURCEROOTORDER_CONNECTION_ID: ConnectionId = ConnectionId.create(ContentRootTestEntity::class.java,
                                                                                   SourceRootTestOrderEntity::class.java,
                                                                                   ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                   false)
    internal val SOURCEROOTS_CONNECTION_ID: ConnectionId = ConnectionId.create(ContentRootTestEntity::class.java,
                                                                               SourceRootTestEntity::class.java,
                                                                               ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                               false)
    private val connections = listOf<ConnectionId>(MODULE_CONNECTION_ID, SOURCEROOTORDER_CONNECTION_ID, SOURCEROOTS_CONNECTION_ID)
  }

  override val module: ModuleTestEntity
    get() = snapshot.instrumentation.getParent(MODULE_CONNECTION_ID, this) as? ModuleTestEntity
            ?: error("Parent module not found for ContentRootTestEntity")
  override val sourceRootOrder: SourceRootTestOrderEntity?
    get() = snapshot.instrumentation.getOneChild(SOURCEROOTORDER_CONNECTION_ID, this) as? SourceRootTestOrderEntity
  override val sourceRoots: List<SourceRootTestEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(SOURCEROOTS_CONNECTION_ID, this) as? Sequence<SourceRootTestEntity>)?.toList()
            ?: error("Children list sourceRoots not found for ContentRootTestEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ContentRootTestEntityData?) :
    ModifiableWorkspaceEntityBase<ContentRootTestEntity, ContentRootTestEntityData>(result), ContentRootTestEntityBuilder {
    internal constructor() : this(ContentRootTestEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(MODULE_CONNECTION_ID, this) == null) {
          error("Field ContentRootTestEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field ContentRootTestEntity#module should be initialized")
        }
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(SOURCEROOTS_CONNECTION_ID, this) == null) {
          error("Field ContentRootTestEntity#sourceRoots should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, SOURCEROOTS_CONNECTION_ID)] == null) {
          error("Field ContentRootTestEntity#sourceRoots should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ContentRootTestEntity
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
    override var module: ModuleTestEntityBuilder
      get() = getParent(MODULE_CONNECTION_ID) as? ModuleTestEntityBuilder ?: error("module is null for ContentRootTestEntity")
      set(value) {
        changeParentOfMany(value, MODULE_CONNECTION_ID)
        changedProperty.add("module")
      }
    override var sourceRootOrder: SourceRootTestOrderEntityBuilder?
      get() = getChild(SOURCEROOTORDER_CONNECTION_ID) as? SourceRootTestOrderEntityBuilder?
      set(value) {
        changeChild(value, SOURCEROOTORDER_CONNECTION_ID)
        changedProperty.add("sourceRootOrder")
      }
    override var sourceRoots: List<SourceRootTestEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(SOURCEROOTS_CONNECTION_ID) as List<SourceRootTestEntityBuilder>
      set(value) {
        changeChildren(value, SOURCEROOTS_CONNECTION_ID)
        changedProperty.add("sourceRoots")
      }

    override fun getEntityClass(): Class<ContentRootTestEntity> = ContentRootTestEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ContentRootTestEntityData : WorkspaceEntityData<ContentRootTestEntity>() {
  override fun newInstance(): ContentRootTestEntity = ContentRootTestEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ContentRootTestEntity, *> = ContentRootTestEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ContentRootTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ContentRootTestEntity(entitySource) {
      parents.filterIsInstance<ModuleTestEntityBuilder>().singleOrNull()?.let { this.module = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleTestEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ContentRootTestEntityData
    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ContentRootTestEntityData
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
