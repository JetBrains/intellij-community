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
import com.intellij.platform.workspace.storage.testEntities.entities.DataClassWithVfus
import com.intellij.platform.workspace.storage.testEntities.entities.EntityWithUnindexedVfu
import com.intellij.platform.workspace.storage.testEntities.entities.EntityWithUnindexedVfuBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithUnindexedVfuImpl(private val dataSource: EntityWithUnindexedVfuData) : EntityWithUnindexedVfu,
                                                                                                WorkspaceEntityBase(dataSource) {

  override val indexedVfu: VirtualFileUrl
    get() {
      readField("indexedVfu")
      return dataSource.indexedVfu
    }
  override val unindexedVfu: VirtualFileUrl
    get() {
      readField("unindexedVfu")
      return dataSource.unindexedVfu
    }
  override val indexedDataClass: DataClassWithVfus
    get() {
      readField("indexedDataClass")
      return dataSource.indexedDataClass
    }
  override val unindexedDataClass: DataClassWithVfus
    get() {
      readField("unindexedDataClass")
      return dataSource.unindexedDataClass
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: EntityWithUnindexedVfuData?) :
    ModifiableWorkspaceEntityBase<EntityWithUnindexedVfu, EntityWithUnindexedVfuData>(result), EntityWithUnindexedVfuBuilder {
    internal constructor() : this(EntityWithUnindexedVfuData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isIndexedVfuInitialized()) {
        error("Field EntityWithUnindexedVfu#indexedVfu should be initialized")
      }
      if (!getEntityData().isUnindexedVfuInitialized()) {
        error("Field EntityWithUnindexedVfu#unindexedVfu should be initialized")
      }
      if (!getEntityData().isIndexedDataClassInitialized()) {
        error("Field EntityWithUnindexedVfu#indexedDataClass should be initialized")
      }
      if (!getEntityData().isUnindexedDataClassInitialized()) {
        error("Field EntityWithUnindexedVfu#unindexedDataClass should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as EntityWithUnindexedVfu
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.indexedVfu != dataSource.indexedVfu) this.indexedVfu = dataSource.indexedVfu
      if (this.unindexedVfu != dataSource.unindexedVfu) this.unindexedVfu = dataSource.unindexedVfu
      if (this.indexedDataClass != dataSource.indexedDataClass) this.indexedDataClass = dataSource.indexedDataClass
      if (this.unindexedDataClass != dataSource.unindexedDataClass) this.unindexedDataClass = dataSource.unindexedDataClass
      updateChildToParentReferences(parents)
    }

    override fun index() {
      index(this, "indexedVfu", this.indexedVfu)
      index(this, "indexedDataClass.vfu", this.indexedDataClass.vfu)
      index(this, "indexedDataClass.vfus", this.indexedDataClass.vfus)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var indexedVfu: VirtualFileUrl
      get() = getEntityData().indexedVfu
      set(value) {
        checkModificationAllowed()
        getEntityData(true).indexedVfu = value
        changedProperty.add("indexedVfu")
        val _diff = diff
        if (_diff != null) {
          index(this, "indexedVfu", value)
        }
      }
    override var unindexedVfu: VirtualFileUrl
      get() = getEntityData().unindexedVfu
      set(value) {
        checkModificationAllowed()
        getEntityData(true).unindexedVfu = value
        changedProperty.add("unindexedVfu")
      }
    override var indexedDataClass: DataClassWithVfus
      get() = getEntityData().indexedDataClass
      set(value) {
        checkModificationAllowed()
        getEntityData(true).indexedDataClass = value
        changedProperty.add("indexedDataClass")
        val _diff = diff
        if (_diff != null) {
          index(this, "indexedDataClass.vfu", value.vfu)
          index(this, "indexedDataClass.vfus", value.vfus)
        }
      }
    override var unindexedDataClass: DataClassWithVfus
      get() = getEntityData().unindexedDataClass
      set(value) {
        checkModificationAllowed()
        getEntityData(true).unindexedDataClass = value
        changedProperty.add("unindexedDataClass")
      }

    override fun getEntityClass(): Class<EntityWithUnindexedVfu> = EntityWithUnindexedVfu::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithUnindexedVfuData : WorkspaceEntityData<EntityWithUnindexedVfu>() {
  lateinit var indexedVfu: VirtualFileUrl
  lateinit var unindexedVfu: VirtualFileUrl
  lateinit var indexedDataClass: DataClassWithVfus
  lateinit var unindexedDataClass: DataClassWithVfus
  internal fun isIndexedVfuInitialized(): Boolean = ::indexedVfu.isInitialized
  internal fun isUnindexedVfuInitialized(): Boolean = ::unindexedVfu.isInitialized
  internal fun isIndexedDataClassInitialized(): Boolean = ::indexedDataClass.isInitialized
  internal fun isUnindexedDataClassInitialized(): Boolean = ::unindexedDataClass.isInitialized
  override fun newInstance(): EntityWithUnindexedVfu = EntityWithUnindexedVfuImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<EntityWithUnindexedVfu, *> = EntityWithUnindexedVfuImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.EntityWithUnindexedVfu") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return EntityWithUnindexedVfu::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return EntityWithUnindexedVfu(indexedVfu, unindexedVfu, indexedDataClass, unindexedDataClass, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as EntityWithUnindexedVfuData
    if (this.entitySource != other.entitySource) return false
    if (this.indexedVfu != other.indexedVfu) return false
    if (this.unindexedVfu != other.unindexedVfu) return false
    if (this.indexedDataClass != other.indexedDataClass) return false
    if (this.unindexedDataClass != other.unindexedDataClass) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as EntityWithUnindexedVfuData
    if (this.indexedVfu != other.indexedVfu) return false
    if (this.unindexedVfu != other.unindexedVfu) return false
    if (this.indexedDataClass != other.indexedDataClass) return false
    if (this.unindexedDataClass != other.unindexedDataClass) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + indexedVfu.hashCode()
    result = 31 * result + unindexedVfu.hashCode()
    result = 31 * result + indexedDataClass.hashCode()
    result = 31 * result + unindexedDataClass.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + indexedVfu.hashCode()
    result = 31 * result + unindexedVfu.hashCode()
    result = 31 * result + indexedDataClass.hashCode()
    result = 31 * result + unindexedDataClass.hashCode()
    return result
  }
}
