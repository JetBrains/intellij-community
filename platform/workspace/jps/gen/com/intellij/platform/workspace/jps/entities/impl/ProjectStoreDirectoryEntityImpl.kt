// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.platform.workspace.jps.entities.ProjectStoreDirectoryEntity
import com.intellij.platform.workspace.jps.entities.ProjectStoreDirectoryEntityBuilder
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
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ProjectStoreDirectoryEntityImpl(private val dataSource: ProjectStoreDirectoryEntityData) : ProjectStoreDirectoryEntity,
                                                                                                          WorkspaceEntityBase(dataSource) {

  override val url: VirtualFileUrl
    get() {
      readField("url")
      return dataSource.url
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: ProjectStoreDirectoryEntityData?) :
    ModifiableWorkspaceEntityBase<ProjectStoreDirectoryEntity, ProjectStoreDirectoryEntityData>(result),
    ProjectStoreDirectoryEntityBuilder {
    internal constructor() : this(ProjectStoreDirectoryEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isUrlInitialized()) {
        error("Field ProjectStoreDirectoryEntity#url should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ProjectStoreDirectoryEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.url != dataSource.url) this.url = dataSource.url
      updateChildToParentReferences(parents)
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
      }

    override fun getEntityClass(): Class<ProjectStoreDirectoryEntity> = ProjectStoreDirectoryEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ProjectStoreDirectoryEntityData : WorkspaceEntityData<ProjectStoreDirectoryEntity>() {
  lateinit var url: VirtualFileUrl
  internal fun isUrlInitialized(): Boolean = ::url.isInitialized
  override fun newInstance(): ProjectStoreDirectoryEntity = ProjectStoreDirectoryEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ProjectStoreDirectoryEntity, *> =
    ProjectStoreDirectoryEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.jps.entities.ProjectStoreDirectoryEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ProjectStoreDirectoryEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ProjectStoreDirectoryEntity(url, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ProjectStoreDirectoryEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.url != other.url) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ProjectStoreDirectoryEntityData
    if (this.url != other.url) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + url.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + url.hashCode()
    return result
  }
}
