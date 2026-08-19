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
import com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntity
import com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionEntity
import com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionSymbolicId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class HeadAbstractionEntityImpl(private val dataSource: HeadAbstractionEntityData) : HeadAbstractionEntity,
                                                                                              WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(HeadAbstractionEntity::class.java,
                                                                         CompositeBaseEntity::class.java,
                                                                         ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                         true)
    private val connections = listOf<ConnectionId>(CHILD_CONNECTION_ID)
  }

  override val symbolicId: HeadAbstractionSymbolicId = super.symbolicId

  override val data: String
    get() {
      readField("data")
      return dataSource.data
    }
  override val child: CompositeBaseEntity?
    get() = snapshot.instrumentation.getOneChild(CHILD_CONNECTION_ID, this) as? CompositeBaseEntity
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: HeadAbstractionEntityData?) :
    ModifiableWorkspaceEntityBase<HeadAbstractionEntity, HeadAbstractionEntityData>(result), HeadAbstractionEntityBuilder {
    internal constructor() : this(HeadAbstractionEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isDataInitialized()) {
        error("Field HeadAbstractionEntity#data should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as HeadAbstractionEntity
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
    override var child: CompositeBaseEntityBuilder<out CompositeBaseEntity>?
      get() = getChild(CHILD_CONNECTION_ID) as? CompositeBaseEntityBuilder<out CompositeBaseEntity>?
      set(value) {
        changeChild(value, CHILD_CONNECTION_ID)
        changedProperty.add("child")
      }

    override fun getEntityClass(): Class<HeadAbstractionEntity> = HeadAbstractionEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class HeadAbstractionEntityData : WorkspaceEntityData<HeadAbstractionEntity>() {
  lateinit var data: String
  internal fun isDataInitialized(): Boolean = ::data.isInitialized
  override fun newInstance(): HeadAbstractionEntity = HeadAbstractionEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<HeadAbstractionEntity, *> = HeadAbstractionEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return HeadAbstractionEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return HeadAbstractionEntity(data, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as HeadAbstractionEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as HeadAbstractionEntityData
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
