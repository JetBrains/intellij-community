// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl

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
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEntity
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEnum

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SubsetEnumEntityImpl(private val dataSource: SubsetEnumEntityData) : SubsetEnumEntity, WorkspaceEntityBase(dataSource) {

  override val someEnum: SubsetEnumEnum
    get() {
      readField("someEnum")
      return dataSource.someEnum
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: SubsetEnumEntityData?) : ModifiableWorkspaceEntityBase<SubsetEnumEntity, SubsetEnumEntityData>(result),
                                                          SubsetEnumEntityBuilder {
    internal constructor() : this(SubsetEnumEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isSomeEnumInitialized()) {
        error("Field SubsetEnumEntity#someEnum should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SubsetEnumEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.someEnum != dataSource.someEnum) this.someEnum = dataSource.someEnum
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var someEnum: SubsetEnumEnum
      get() = getEntityData().someEnum
      set(value) {
        checkModificationAllowed()
        getEntityData(true).someEnum = value
        changedProperty.add("someEnum")
      }

    override fun getEntityClass(): Class<SubsetEnumEntity> = SubsetEnumEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SubsetEnumEntityData : WorkspaceEntityData<SubsetEnumEntity>() {
  lateinit var someEnum: SubsetEnumEnum
  internal fun isSomeEnumInitialized(): Boolean = ::someEnum.isInitialized
  override fun newInstance(): SubsetEnumEntity = SubsetEnumEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<SubsetEnumEntity, *> = SubsetEnumEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SubsetEnumEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return SubsetEnumEntity(someEnum, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SubsetEnumEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.someEnum != other.someEnum) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SubsetEnumEntityData
    if (this.someEnum != other.someEnum) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + someEnum.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + someEnum.hashCode()
    return result
  }
}
