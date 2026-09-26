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
import com.intellij.platform.workspace.storage.testEntities.entities.CompositeAbstractEntity
import com.intellij.platform.workspace.storage.testEntities.entities.CompositeAbstractEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.SimpleAbstractEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SimpleChildAbstractEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SimpleChildAbstractEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleChildAbstractEntityImpl(private val dataSource: SimpleChildAbstractEntityData) : SimpleChildAbstractEntity,
                                                                                                      WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENTINLIST_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositeAbstractEntity::class.java,
                                                                                SimpleAbstractEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                true)
    private val connections = listOf<ConnectionId>(PARENTINLIST_CONNECTION_ID)
  }

  override val parentInList: CompositeAbstractEntity?
    get() = snapshot.instrumentation.getParent(PARENTINLIST_CONNECTION_ID, this) as? CompositeAbstractEntity
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: SimpleChildAbstractEntityData?) :
    ModifiableWorkspaceEntityBase<SimpleChildAbstractEntity, SimpleChildAbstractEntityData>(result), SimpleChildAbstractEntityBuilder {
    internal constructor() : this(SimpleChildAbstractEntityData())

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
      dataSource as SimpleChildAbstractEntity
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
      @Suppress("UNCHECKED_CAST")
      get() = getParent(PARENTINLIST_CONNECTION_ID) as? CompositeAbstractEntityBuilder<out CompositeAbstractEntity>?
              ?: error("parentInList is null for SimpleAbstractEntity")
      set(value) {
        changeParentOfMany(value, PARENTINLIST_CONNECTION_ID)
        changedProperty.add("parentInList")
      }

    override fun getEntityClass(): Class<SimpleChildAbstractEntity> = SimpleChildAbstractEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleChildAbstractEntityData : WorkspaceEntityData<SimpleChildAbstractEntity>() {
  override fun newInstance(): SimpleChildAbstractEntity = SimpleChildAbstractEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<SimpleChildAbstractEntity, *> =
    SimpleChildAbstractEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.SimpleChildAbstractEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SimpleChildAbstractEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return SimpleChildAbstractEntity(entitySource) {
      this.parentInList = parents.filterIsInstance<CompositeAbstractEntityBuilder<out CompositeAbstractEntity>>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SimpleChildAbstractEntityData
    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SimpleChildAbstractEntityData
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
