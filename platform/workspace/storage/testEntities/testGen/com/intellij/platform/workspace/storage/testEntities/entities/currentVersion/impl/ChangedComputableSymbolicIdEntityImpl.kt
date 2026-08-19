// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl

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
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputableSymbolicId
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputableSymbolicIdEntity
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputableSymbolicIdEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChangedComputableSymbolicIdEntityImpl(private val dataSource: ChangedComputableSymbolicIdEntityData) :
  ChangedComputableSymbolicIdEntity, WorkspaceEntityBase(dataSource) {
  override val symbolicId: ChangedComputableSymbolicId = super.symbolicId

  override val text: String
    get() {
      readField("text")
      return dataSource.text
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: ChangedComputableSymbolicIdEntityData?) :
    ModifiableWorkspaceEntityBase<ChangedComputableSymbolicIdEntity, ChangedComputableSymbolicIdEntityData>(result),
    ChangedComputableSymbolicIdEntityBuilder {
    internal constructor() : this(ChangedComputableSymbolicIdEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isTextInitialized()) {
        error("Field ChangedComputableSymbolicIdEntity#text should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ChangedComputableSymbolicIdEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.text != dataSource.text) this.text = dataSource.text
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var text: String
      get() = getEntityData().text
      set(value) {
        checkModificationAllowed()
        getEntityData(true).text = value
        changedProperty.add("text")
      }

    override fun getEntityClass(): Class<ChangedComputableSymbolicIdEntity> = ChangedComputableSymbolicIdEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ChangedComputableSymbolicIdEntityData : WorkspaceEntityData<ChangedComputableSymbolicIdEntity>() {
  lateinit var text: String
  internal fun isTextInitialized(): Boolean = ::text.isInitialized
  override fun newInstance(): ChangedComputableSymbolicIdEntity = ChangedComputableSymbolicIdEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ChangedComputableSymbolicIdEntity, *> =
    ChangedComputableSymbolicIdEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputableSymbolicIdEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ChangedComputableSymbolicIdEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ChangedComputableSymbolicIdEntity(text, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ChangedComputableSymbolicIdEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.text != other.text) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ChangedComputableSymbolicIdEntityData
    if (this.text != other.text) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + text.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + text.hashCode()
    return result
  }
}
