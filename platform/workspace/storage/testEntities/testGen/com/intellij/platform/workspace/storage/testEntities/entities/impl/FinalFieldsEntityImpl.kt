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
import com.intellij.platform.workspace.storage.testEntities.entities.AnotherDataClass
import com.intellij.platform.workspace.storage.testEntities.entities.FinalFieldsEntity
import com.intellij.platform.workspace.storage.testEntities.entities.FinalFieldsEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class FinalFieldsEntityImpl(private val dataSource: FinalFieldsEntityData) : FinalFieldsEntity, WorkspaceEntityBase(dataSource) {

  override val descriptor: AnotherDataClass
    get() {
      readField("descriptor")
      return dataSource.descriptor
    }
  override var description: String = dataSource.description
  override var anotherVersion: Int = dataSource.anotherVersion
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: FinalFieldsEntityData?) : ModifiableWorkspaceEntityBase<FinalFieldsEntity, FinalFieldsEntityData>(result),
                                                           FinalFieldsEntityBuilder {
    internal constructor() : this(FinalFieldsEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isDescriptorInitialized()) {
        error("Field FinalFieldsEntity#descriptor should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as FinalFieldsEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.descriptor != dataSource.descriptor) this.descriptor = dataSource.descriptor
      if (this.description != dataSource.description) this.description = dataSource.description
      if (this.anotherVersion != dataSource.anotherVersion) this.anotherVersion = dataSource.anotherVersion
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var descriptor: AnotherDataClass
      get() = getEntityData().descriptor
      set(value) {
        checkModificationAllowed()
        getEntityData(true).descriptor = value
        changedProperty.add("descriptor")
      }
    override var description: String
      get() = getEntityData().description
      set(value) {
        checkModificationAllowed()
        getEntityData(true).description = value
        changedProperty.add("description")
      }
    override var anotherVersion: Int
      get() = getEntityData().anotherVersion
      set(value) {
        checkModificationAllowed()
        getEntityData(true).anotherVersion = value
        changedProperty.add("anotherVersion")
      }

    override fun getEntityClass(): Class<FinalFieldsEntity> = FinalFieldsEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class FinalFieldsEntityData : WorkspaceEntityData<FinalFieldsEntity>() {
  lateinit var descriptor: AnotherDataClass
  var description: String = "Default description"
  var anotherVersion: Int = 0
  internal fun isDescriptorInitialized(): Boolean = ::descriptor.isInitialized
  override fun newInstance(): FinalFieldsEntity = FinalFieldsEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<FinalFieldsEntity, *> = FinalFieldsEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.FinalFieldsEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return FinalFieldsEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return FinalFieldsEntity(descriptor, entitySource) {
      this.description = this@FinalFieldsEntityData.description
      this.anotherVersion = this@FinalFieldsEntityData.anotherVersion
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as FinalFieldsEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.descriptor != other.descriptor) return false
    if (this.description != other.description) return false
    if (this.anotherVersion != other.anotherVersion) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as FinalFieldsEntityData
    if (this.descriptor != other.descriptor) return false
    if (this.description != other.description) return false
    if (this.anotherVersion != other.anotherVersion) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + descriptor.hashCode()
    result = 31 * result + description.hashCode()
    result = 31 * result + anotherVersion.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + descriptor.hashCode()
    result = 31 * result + description.hashCode()
    result = 31 * result + anotherVersion.hashCode()
    return result
  }
}
