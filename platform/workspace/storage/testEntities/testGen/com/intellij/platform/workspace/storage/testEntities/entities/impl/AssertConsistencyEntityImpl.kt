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
import com.intellij.platform.workspace.storage.testEntities.entities.AssertConsistencyEntity
import com.intellij.platform.workspace.storage.testEntities.entities.AssertConsistencyEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class AssertConsistencyEntityImpl(private val dataSource: AssertConsistencyEntityData) : AssertConsistencyEntity,
                                                                                                  WorkspaceEntityBase(dataSource) {

  override val passCheck: Boolean
    get() {
      readField("passCheck")
      return dataSource.passCheck
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: AssertConsistencyEntityData?) :
    ModifiableWorkspaceEntityBase<AssertConsistencyEntity, AssertConsistencyEntityData>(result), AssertConsistencyEntityBuilder {
    internal constructor() : this(AssertConsistencyEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as AssertConsistencyEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.passCheck != dataSource.passCheck) this.passCheck = dataSource.passCheck
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var passCheck: Boolean
      get() = getEntityData().passCheck
      set(value) {
        checkModificationAllowed()
        getEntityData(true).passCheck = value
        changedProperty.add("passCheck")
      }

    override fun getEntityClass(): Class<AssertConsistencyEntity> = AssertConsistencyEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class AssertConsistencyEntityData : WorkspaceEntityData<AssertConsistencyEntity>() {
  var passCheck: Boolean = false
  override fun newInstance(): AssertConsistencyEntity = AssertConsistencyEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<AssertConsistencyEntity, *> = AssertConsistencyEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.AssertConsistencyEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return AssertConsistencyEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return AssertConsistencyEntity(passCheck, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as AssertConsistencyEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.passCheck != other.passCheck) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as AssertConsistencyEntityData
    if (this.passCheck != other.passCheck) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + passCheck.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + passCheck.hashCode()
    return result
  }
}
