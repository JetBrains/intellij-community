// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ContentRootEntityBuilder
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SourceRootEntityImpl(private val dataSource: SourceRootEntityData) : SourceRootEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CONTENTROOT_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ContentRootEntity::class.java, SourceRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(CONTENTROOT_CONNECTION_ID)
  }

  override val url: VirtualFileUrl
    get() {
      readField("url")
      return dataSource.url
    }
  override val rootTypeId: SourceRootTypeId
    get() {
      readField("rootTypeId")
      return dataSource.rootTypeId
    }
  override val contentRoot: ContentRootEntity
    get() = snapshot.instrumentation.getParent(CONTENTROOT_CONNECTION_ID, this) as? ContentRootEntity
            ?: error("Parent contentRoot not found for SourceRootEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: SourceRootEntityData?) : ModifiableWorkspaceEntityBase<SourceRootEntity, SourceRootEntityData>(result),
                                                          SourceRootEntity.Builder {
    internal constructor() : this(SourceRootEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isUrlInitialized()) {
        error("Field SourceRootEntity#url should be initialized")
      }
      if (!getEntityData().isRootTypeIdInitialized()) {
        error("Field SourceRootEntity#rootTypeId should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(CONTENTROOT_CONNECTION_ID, this) == null) {
          error("Field SourceRootEntity#contentRoot should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] == null) {
          error("Field SourceRootEntity#contentRoot should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SourceRootEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.url != dataSource.url) this.url = dataSource.url
      if (this.rootTypeId != dataSource.rootTypeId) this.rootTypeId = dataSource.rootTypeId
      updateChildToParentReferences(parents)
    }

    override fun index() {
      index(this, "url", this.url)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var url: VirtualFileUrl
      get() = getEntityData().url
      set(value) {
        checkModificationAllowed()
        getEntityData(true).url = value
        changedProperty.add("url")
        val _diff = diff
        if (_diff != null) index(this, "url", value)
      }
    override var rootTypeId: SourceRootTypeId
      get() = getEntityData().rootTypeId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).rootTypeId = value
        changedProperty.add("rootTypeId")
      }
    override var contentRoot: ContentRootEntityBuilder
      get() = getParent(CONTENTROOT_CONNECTION_ID) as? ContentRootEntityBuilder ?: error("contentRoot is null for SourceRootEntity")
      set(value) {
        changeParentOfMany(value, CONTENTROOT_CONNECTION_ID)
        changedProperty.add("contentRoot")
      }

    override fun getEntityClass(): Class<SourceRootEntity> = SourceRootEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SourceRootEntityData : WorkspaceEntityData<SourceRootEntity>() {
  lateinit var url: VirtualFileUrl
  lateinit var rootTypeId: SourceRootTypeId
  internal fun isUrlInitialized(): Boolean = ::url.isInitialized
  internal fun isRootTypeIdInitialized(): Boolean = ::rootTypeId.isInitialized
  override fun newInstance(): SourceRootEntity = SourceRootEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<SourceRootEntity, *> = SourceRootEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.jps.entities.SourceRootEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SourceRootEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return SourceRootEntity(url, rootTypeId, entitySource) {
      parents.filterIsInstance<ContentRootEntityBuilder>().singleOrNull()?.let { this.contentRoot = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ContentRootEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SourceRootEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.url != other.url) return false
    if (this.rootTypeId != other.rootTypeId) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SourceRootEntityData
    if (this.url != other.url) return false
    if (this.rootTypeId != other.rootTypeId) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + rootTypeId.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + rootTypeId.hashCode()
    return result
  }
}
