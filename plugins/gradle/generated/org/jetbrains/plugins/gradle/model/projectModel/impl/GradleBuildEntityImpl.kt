// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package org.jetbrains.plugins.gradle.model.projectModel.impl

import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityBuilder
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityBuilder
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleBuildEntityImpl(private val dataSource: GradleBuildEntityData) : GradleBuildEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val EXTERNALPROJECT_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ExternalProjectEntity::class.java, GradleBuildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val PROJECTS_CONNECTION_ID: ConnectionId =
      ConnectionId.create(GradleBuildEntity::class.java, GradleProjectEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(EXTERNALPROJECT_CONNECTION_ID, PROJECTS_CONNECTION_ID)
  }

  override val symbolicId: GradleBuildEntityId = super.symbolicId

  override val externalProject: ExternalProjectEntity
    get() = snapshot.instrumentation.getParent(EXTERNALPROJECT_CONNECTION_ID, this) as? ExternalProjectEntity
            ?: error("Parent externalProject not found for GradleBuildEntity")
  override val externalProjectId: ExternalProjectEntityId
    get() {
      readField("externalProjectId")
      return dataSource.externalProjectId
    }
  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }
  override val url: VirtualFileUrl
    get() {
      readField("url")
      return dataSource.url
    }
  override val projects: List<GradleProjectEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(PROJECTS_CONNECTION_ID, this) as? Sequence<GradleProjectEntity>)?.toList()
            ?: error("Children list projects not found for GradleBuildEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: GradleBuildEntityData?) : ModifiableWorkspaceEntityBase<GradleBuildEntity, GradleBuildEntityData>(result),
                                                           GradleBuildEntityBuilder {
    internal constructor() : this(GradleBuildEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(EXTERNALPROJECT_CONNECTION_ID, this) == null) {
          error("Field GradleBuildEntity#externalProject should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, EXTERNALPROJECT_CONNECTION_ID)] == null) {
          error("Field GradleBuildEntity#externalProject should be initialized")
        }
      }
      if (!getEntityData().isExternalProjectIdInitialized()) {
        error("Field GradleBuildEntity#externalProjectId should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field GradleBuildEntity#name should be initialized")
      }
      if (!getEntityData().isUrlInitialized()) {
        error("Field GradleBuildEntity#url should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(PROJECTS_CONNECTION_ID, this) == null) {
          error("Field GradleBuildEntity#projects should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, PROJECTS_CONNECTION_ID)] == null) {
          error("Field GradleBuildEntity#projects should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as GradleBuildEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.externalProjectId != dataSource.externalProjectId) this.externalProjectId = dataSource.externalProjectId
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.url != dataSource.url) this.url = dataSource.url
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
    override var externalProject: ExternalProjectEntityBuilder
      get() = getParent(EXTERNALPROJECT_CONNECTION_ID) as? ExternalProjectEntityBuilder
              ?: error("externalProject is null for GradleBuildEntity")
      set(value) {
        changeParentOfMany(value, EXTERNALPROJECT_CONNECTION_ID)
        changedProperty.add("externalProject")
      }
    override var externalProjectId: ExternalProjectEntityId
      get() = getEntityData().externalProjectId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).externalProjectId = value
        changedProperty.add("externalProjectId")
      }
    override var name: String
      get() = getEntityData().name
      set(value) {
        checkModificationAllowed()
        getEntityData(true).name = value
        changedProperty.add("name")
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
    override var projects: List<GradleProjectEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(PROJECTS_CONNECTION_ID) as List<GradleProjectEntityBuilder>
      set(value) {
        changeChildren(value, PROJECTS_CONNECTION_ID)
        changedProperty.add("projects")
      }

    override fun getEntityClass(): Class<GradleBuildEntity> = GradleBuildEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleBuildEntityData : WorkspaceEntityData<GradleBuildEntity>(), SoftLinkable {
  lateinit var externalProjectId: ExternalProjectEntityId
  lateinit var name: String
  lateinit var url: VirtualFileUrl
  internal fun isExternalProjectIdInitialized(): Boolean = ::externalProjectId.isInitialized
  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  internal fun isUrlInitialized(): Boolean = ::url.isInitialized
  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    result.add(externalProjectId)
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    index.index(this, externalProjectId)
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    val mutablePreviousSet = HashSet(prev)
    val removedItem_externalProjectId = mutablePreviousSet.remove(externalProjectId)
    if (!removedItem_externalProjectId) {
      index.index(this, externalProjectId)
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val externalProjectId_data = if (externalProjectId == oldLink) {
      changed = true
      newLink as ExternalProjectEntityId
    }
    else {
      null
    }
    if (externalProjectId_data != null) {
      externalProjectId = externalProjectId_data
    }
    return changed
  }

  override fun newInstance(): GradleBuildEntity = GradleBuildEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<GradleBuildEntity, *> = GradleBuildEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return GradleBuildEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return GradleBuildEntity(externalProjectId, name, url, entitySource) {
      parents.filterIsInstance<ExternalProjectEntityBuilder>().singleOrNull()?.let { this.externalProject = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ExternalProjectEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as GradleBuildEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.externalProjectId != other.externalProjectId) return false
    if (this.name != other.name) return false
    if (this.url != other.url) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as GradleBuildEntityData
    if (this.externalProjectId != other.externalProjectId) return false
    if (this.name != other.name) return false
    if (this.url != other.url) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + externalProjectId.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + url.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + externalProjectId.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + url.hashCode()
    return result
  }
}
