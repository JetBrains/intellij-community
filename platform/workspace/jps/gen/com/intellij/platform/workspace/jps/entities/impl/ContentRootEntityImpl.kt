// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntityBuilder
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
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ContentRootEntityImpl(private val dataSource: ContentRootEntityData) : ContentRootEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleEntity::class.java, ContentRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val SOURCEROOTS_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ContentRootEntity::class.java, SourceRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val EXCLUDEDURLS_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ContentRootEntity::class.java, ExcludeUrlEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
    private val connections = listOf<ConnectionId>(MODULE_CONNECTION_ID, SOURCEROOTS_CONNECTION_ID, EXCLUDEDURLS_CONNECTION_ID)
  }

  override val url: VirtualFileUrl
    get() {
      readField("url")
      return dataSource.url
    }
  override val excludedPatterns: List<String>
    get() {
      readField("excludedPatterns")
      return dataSource.excludedPatterns
    }
  override val module: ModuleEntity
    get() = snapshot.instrumentation.getParent(MODULE_CONNECTION_ID, this) as? ModuleEntity
            ?: error("Parent module not found for ContentRootEntity")
  override val sourceRoots: List<SourceRootEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(SOURCEROOTS_CONNECTION_ID, this) as? Sequence<SourceRootEntity>)?.toList()
            ?: error("Children list sourceRoots not found for ContentRootEntity")
  override val excludedUrls: List<ExcludeUrlEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(EXCLUDEDURLS_CONNECTION_ID, this) as? Sequence<ExcludeUrlEntity>)?.toList()
            ?: error("Children list excludedUrls not found for ContentRootEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ContentRootEntityData?) : ModifiableWorkspaceEntityBase<ContentRootEntity, ContentRootEntityData>(result),
                                                           ContentRootEntity.Builder {
    internal constructor() : this(ContentRootEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isUrlInitialized()) {
        error("Field ContentRootEntity#url should be initialized")
      }
      if (!getEntityData().isExcludedPatternsInitialized()) {
        error("Field ContentRootEntity#excludedPatterns should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(MODULE_CONNECTION_ID, this) == null) {
          error("Field ContentRootEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field ContentRootEntity#module should be initialized")
        }
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(SOURCEROOTS_CONNECTION_ID, this) == null) {
          error("Field ContentRootEntity#sourceRoots should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, SOURCEROOTS_CONNECTION_ID)] == null) {
          error("Field ContentRootEntity#sourceRoots should be initialized")
        }
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(EXCLUDEDURLS_CONNECTION_ID, this) == null) {
          error("Field ContentRootEntity#excludedUrls should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, EXCLUDEDURLS_CONNECTION_ID)] == null) {
          error("Field ContentRootEntity#excludedUrls should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_excludedPatterns = getEntityData().excludedPatterns
      if (collection_excludedPatterns is MutableWorkspaceList<*>) {
        collection_excludedPatterns.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ContentRootEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.url != dataSource.url) this.url = dataSource.url
      if (this.excludedPatterns != dataSource.excludedPatterns) this.excludedPatterns = dataSource.excludedPatterns.toMutableList()
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
    private val excludedPatternsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("excludedPatterns")
    }
    override var excludedPatterns: MutableList<String>
      get() {
        val collection_excludedPatterns = getEntityData().excludedPatterns
        if (collection_excludedPatterns !is MutableWorkspaceList) return collection_excludedPatterns
        if (diff == null || modifiable.get()) {
          collection_excludedPatterns.setModificationUpdateAction(excludedPatternsUpdater)
        }
        else {
          collection_excludedPatterns.cleanModificationUpdateAction()
        }
        return collection_excludedPatterns
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).excludedPatterns = value
        excludedPatternsUpdater.invoke(value)
      }
    override var module: ModuleEntityBuilder
      get() = getParent(MODULE_CONNECTION_ID) as? ModuleEntityBuilder ?: error("module is null for ContentRootEntity")
      set(value) {
        changeParentOfMany(value, MODULE_CONNECTION_ID)
        changedProperty.add("module")
      }
    override var sourceRoots: List<SourceRootEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(SOURCEROOTS_CONNECTION_ID) as List<SourceRootEntityBuilder>
      set(value) {
        changeChildren(value, SOURCEROOTS_CONNECTION_ID)
        changedProperty.add("sourceRoots")
      }
    override var excludedUrls: List<ExcludeUrlEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(EXCLUDEDURLS_CONNECTION_ID) as List<ExcludeUrlEntityBuilder>
      set(value) {
        changeChildren(value, EXCLUDEDURLS_CONNECTION_ID)
        changedProperty.add("excludedUrls")
      }

    override fun getEntityClass(): Class<ContentRootEntity> = ContentRootEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ContentRootEntityData : WorkspaceEntityData<ContentRootEntity>() {
  lateinit var url: VirtualFileUrl
  lateinit var excludedPatterns: MutableList<String>
  internal fun isUrlInitialized(): Boolean = ::url.isInitialized
  internal fun isExcludedPatternsInitialized(): Boolean = ::excludedPatterns.isInitialized
  override fun newInstance(): ContentRootEntity = ContentRootEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ContentRootEntity, *> = ContentRootEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.jps.entities.ContentRootEntity") as EntityMetadata
  }

  override fun clone(): ContentRootEntityData {
    val clonedEntity = super.clone()
    clonedEntity as ContentRootEntityData
    clonedEntity.excludedPatterns = clonedEntity.excludedPatterns.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ContentRootEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ContentRootEntity(url, excludedPatterns, entitySource) {
      parents.filterIsInstance<ModuleEntityBuilder>().singleOrNull()?.let { this.module = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ContentRootEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.url != other.url) return false
    if (this.excludedPatterns != other.excludedPatterns) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ContentRootEntityData
    if (this.url != other.url) return false
    if (this.excludedPatterns != other.excludedPatterns) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + excludedPatterns.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + excludedPatterns.hashCode()
    return result
  }

  override fun equalsByKey(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ContentRootEntityData
    if (this.url != other.url) return false
    return true
  }

  override fun hashCodeByKey(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + url.hashCode()
    return result
  }
}
