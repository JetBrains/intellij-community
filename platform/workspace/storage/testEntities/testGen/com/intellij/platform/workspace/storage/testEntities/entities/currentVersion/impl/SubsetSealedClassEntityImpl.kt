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
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClassEntity
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClassEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SubsetSealedClassEntityImpl(private val dataSource: SubsetSealedClassEntityData) : SubsetSealedClassEntity,
                                                                                                  WorkspaceEntityBase(dataSource) {

  override val someData: SubsetSealedClass
    get() {
      readField("someData")
      return dataSource.someData
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: SubsetSealedClassEntityData?) :
    ModifiableWorkspaceEntityBase<SubsetSealedClassEntity, SubsetSealedClassEntityData>(result), SubsetSealedClassEntityBuilder {
    internal constructor() : this(SubsetSealedClassEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isSomeDataInitialized()) {
        error("Field SubsetSealedClassEntity#someData should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SubsetSealedClassEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.someData != dataSource.someData) this.someData = dataSource.someData
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var someData: SubsetSealedClass
      get() = getEntityData().someData
      set(value) {
        checkModificationAllowed()
        getEntityData(true).someData = value
        changedProperty.add("someData")
      }

    override fun getEntityClass(): Class<SubsetSealedClassEntity> = SubsetSealedClassEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SubsetSealedClassEntityData : WorkspaceEntityData<SubsetSealedClassEntity>() {
  lateinit var someData: SubsetSealedClass
  internal fun isSomeDataInitialized(): Boolean = ::someData.isInitialized
  override fun newInstance(): SubsetSealedClassEntity = SubsetSealedClassEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<SubsetSealedClassEntity, *> = SubsetSealedClassEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClassEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SubsetSealedClassEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return SubsetSealedClassEntity(someData, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SubsetSealedClassEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.someData != other.someData) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SubsetSealedClassEntityData
    if (this.someData != other.someData) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + someData.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + someData.hashCode()
    return result
  }
}
