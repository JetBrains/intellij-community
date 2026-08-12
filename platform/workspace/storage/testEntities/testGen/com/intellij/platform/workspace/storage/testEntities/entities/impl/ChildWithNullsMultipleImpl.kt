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
import com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNullsMultiple
import com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNullsMultipleBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildWithNullsMultipleImpl(private val dataSource: ChildWithNullsMultipleData) : ChildWithNullsMultiple,
                                                                                                WorkspaceEntityBase(dataSource) {

  override val childData: String
    get() {
      readField("childData")
      return dataSource.childData
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: ChildWithNullsMultipleData?) :
    ModifiableWorkspaceEntityBase<ChildWithNullsMultiple, ChildWithNullsMultipleData>(result), ChildWithNullsMultipleBuilder {
    internal constructor() : this(ChildWithNullsMultipleData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isChildDataInitialized()) {
        error("Field ChildWithNullsMultiple#childData should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ChildWithNullsMultiple
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.childData != dataSource.childData) this.childData = dataSource.childData
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var childData: String
      get() = getEntityData().childData
      set(value) {
        checkModificationAllowed()
        getEntityData(true).childData = value
        changedProperty.add("childData")
      }

    override fun getEntityClass(): Class<ChildWithNullsMultiple> = ChildWithNullsMultiple::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildWithNullsMultipleData : WorkspaceEntityData<ChildWithNullsMultiple>() {
  lateinit var childData: String
  internal fun isChildDataInitialized(): Boolean = ::childData.isInitialized
  override fun newInstance(): ChildWithNullsMultiple = ChildWithNullsMultipleImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ChildWithNullsMultiple, *> = ChildWithNullsMultipleImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNullsMultiple") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ChildWithNullsMultiple::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ChildWithNullsMultiple(childData, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ChildWithNullsMultipleData
    if (this.entitySource != other.entitySource) return false
    if (this.childData != other.childData) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ChildWithNullsMultipleData
    if (this.childData != other.childData) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + childData.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + childData.hashCode()
    return result
  }
}
