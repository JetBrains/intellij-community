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
import com.intellij.platform.workspace.storage.testEntities.entities.OneEntityWithSymbolicId
import com.intellij.platform.workspace.storage.testEntities.entities.OneEntityWithSymbolicIdBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class OneEntityWithSymbolicIdImpl(private val dataSource: OneEntityWithSymbolicIdData) : OneEntityWithSymbolicId,
                                                                                                  WorkspaceEntityBase(dataSource) {
  override val symbolicId: OneSymbolicId = super.symbolicId

  override val myName: String
    get() {
      readField("myName")
      return dataSource.myName
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: OneEntityWithSymbolicIdData?) :
    ModifiableWorkspaceEntityBase<OneEntityWithSymbolicId, OneEntityWithSymbolicIdData>(result), OneEntityWithSymbolicIdBuilder {
    internal constructor() : this(OneEntityWithSymbolicIdData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isMyNameInitialized()) {
        error("Field OneEntityWithSymbolicId#myName should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as OneEntityWithSymbolicId
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.myName != dataSource.myName) this.myName = dataSource.myName
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var myName: String
      get() = getEntityData().myName
      set(value) {
        checkModificationAllowed()
        getEntityData(true).myName = value
        changedProperty.add("myName")
      }

    override fun getEntityClass(): Class<OneEntityWithSymbolicId> = OneEntityWithSymbolicId::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class OneEntityWithSymbolicIdData : WorkspaceEntityData<OneEntityWithSymbolicId>() {
  lateinit var myName: String
  internal fun isMyNameInitialized(): Boolean = ::myName.isInitialized
  override fun newInstance(): OneEntityWithSymbolicId = OneEntityWithSymbolicIdImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<OneEntityWithSymbolicId, *> = OneEntityWithSymbolicIdImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.OneEntityWithSymbolicId") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return OneEntityWithSymbolicId::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return OneEntityWithSymbolicId(myName, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as OneEntityWithSymbolicIdData
    if (this.entitySource != other.entitySource) return false
    if (this.myName != other.myName) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as OneEntityWithSymbolicIdData
    if (this.myName != other.myName) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + myName.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + myName.hashCode()
    return result
  }
}
