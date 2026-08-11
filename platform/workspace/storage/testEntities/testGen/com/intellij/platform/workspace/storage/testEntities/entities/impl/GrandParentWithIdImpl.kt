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
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithId
import com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithIdBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class GrandParentWithIdImpl(private val dataSource: GrandParentWithIdData) : GrandParentWithId, WorkspaceEntityBase(dataSource) {
  override val symbolicId: GrandParentWithId.GrandParentId = super.symbolicId

  override val myId: String
    get() {
      readField("myId")
      return dataSource.myId
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: GrandParentWithIdData?) : ModifiableWorkspaceEntityBase<GrandParentWithId, GrandParentWithIdData>(result),
                                                           GrandParentWithIdBuilder {
    internal constructor() : this(GrandParentWithIdData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isMyIdInitialized()) {
        error("Field GrandParentWithId#myId should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as GrandParentWithId
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.myId != dataSource.myId) this.myId = dataSource.myId
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var myId: String
      get() = getEntityData().myId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).myId = value
        changedProperty.add("myId")
      }

    override fun getEntityClass(): Class<GrandParentWithId> = GrandParentWithId::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class GrandParentWithIdData : WorkspaceEntityData<GrandParentWithId>() {
  lateinit var myId: String
  internal fun isMyIdInitialized(): Boolean = ::myId.isInitialized
  override fun newInstance(): GrandParentWithId = GrandParentWithIdImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<GrandParentWithId, *> = GrandParentWithIdImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithId") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return GrandParentWithId::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return GrandParentWithId(myId, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as GrandParentWithIdData
    if (this.entitySource != other.entitySource) return false
    if (this.myId != other.myId) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as GrandParentWithIdData
    if (this.myId != other.myId) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + myId.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + myId.hashCode()
    return result
  }
}
