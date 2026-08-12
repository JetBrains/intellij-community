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
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.EnumPropsEntity
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.EnumPropsEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.EnumPropsEnum

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class EnumPropsEntityImpl(private val dataSource: EnumPropsEntityData) : EnumPropsEntity, WorkspaceEntityBase(dataSource) {

  override val someEnum: EnumPropsEnum
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

  internal class Builder(result: EnumPropsEntityData?) : ModifiableWorkspaceEntityBase<EnumPropsEntity, EnumPropsEntityData>(result),
                                                         EnumPropsEntityBuilder {
    internal constructor() : this(EnumPropsEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isSomeEnumInitialized()) {
        error("Field EnumPropsEntity#someEnum should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as EnumPropsEntity
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
    override var someEnum: EnumPropsEnum
      get() = getEntityData().someEnum
      set(value) {
        checkModificationAllowed()
        getEntityData(true).someEnum = value
        changedProperty.add("someEnum")
      }

    override fun getEntityClass(): Class<EnumPropsEntity> = EnumPropsEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class EnumPropsEntityData : WorkspaceEntityData<EnumPropsEntity>() {
  lateinit var someEnum: EnumPropsEnum
  internal fun isSomeEnumInitialized(): Boolean = ::someEnum.isInitialized
  override fun newInstance(): EnumPropsEntity = EnumPropsEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<EnumPropsEntity, *> = EnumPropsEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.EnumPropsEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return EnumPropsEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return EnumPropsEntity(someEnum, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as EnumPropsEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.someEnum != other.someEnum) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as EnumPropsEntityData
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
