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
import com.intellij.platform.workspace.storage.testEntities.entities.AbstractChildWithLinkToParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.AbstractChildWithLinkToParentEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.ParentWithLinkToAbstractChild
import com.intellij.platform.workspace.storage.testEntities.entities.ParentWithLinkToAbstractChildBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentWithLinkToAbstractChildImpl(private val dataSource: ParentWithLinkToAbstractChildData) : ParentWithLinkToAbstractChild,
                                                                                                              WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentWithLinkToAbstractChild::class.java,
                                                                         AbstractChildWithLinkToParentEntity::class.java,
                                                                         ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                         true)
    private val connections = listOf<ConnectionId>(CHILD_CONNECTION_ID)
  }

  override val data: String
    get() {
      readField("data")
      return dataSource.data
    }
  override val child: AbstractChildWithLinkToParentEntity?
    get() = snapshot.instrumentation.getOneChild(CHILD_CONNECTION_ID, this) as? AbstractChildWithLinkToParentEntity
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ParentWithLinkToAbstractChildData?) :
    ModifiableWorkspaceEntityBase<ParentWithLinkToAbstractChild, ParentWithLinkToAbstractChildData>(result),
    ParentWithLinkToAbstractChildBuilder {
    internal constructor() : this(ParentWithLinkToAbstractChildData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isDataInitialized()) {
        error("Field ParentWithLinkToAbstractChild#data should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ParentWithLinkToAbstractChild
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
    override var child: AbstractChildWithLinkToParentEntityBuilder<out AbstractChildWithLinkToParentEntity>?
      get() = getChild(CHILD_CONNECTION_ID) as? AbstractChildWithLinkToParentEntityBuilder<out AbstractChildWithLinkToParentEntity>?
      set(value) {
        changeChild(value, CHILD_CONNECTION_ID)
        changedProperty.add("child")
      }

    override fun getEntityClass(): Class<ParentWithLinkToAbstractChild> = ParentWithLinkToAbstractChild::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentWithLinkToAbstractChildData : WorkspaceEntityData<ParentWithLinkToAbstractChild>() {
  lateinit var data: String
  internal fun isDataInitialized(): Boolean = ::data.isInitialized
  override fun newInstance(): ParentWithLinkToAbstractChild = ParentWithLinkToAbstractChildImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ParentWithLinkToAbstractChild, *> =
    ParentWithLinkToAbstractChildImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.ParentWithLinkToAbstractChild") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ParentWithLinkToAbstractChild::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ParentWithLinkToAbstractChild(data, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ParentWithLinkToAbstractChildData
    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ParentWithLinkToAbstractChildData
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
