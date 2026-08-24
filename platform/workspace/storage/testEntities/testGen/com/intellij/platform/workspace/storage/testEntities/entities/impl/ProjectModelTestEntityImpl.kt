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
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.Descriptor
import com.intellij.platform.workspace.storage.testEntities.entities.ProjectModelTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ProjectModelTestEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ProjectModelTestEntityImpl(private val dataSource: ProjectModelTestEntityData) : ProjectModelTestEntity,
                                                                                                WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ProjectModelTestEntity::class.java,
                                                                                ProjectModelTestEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                true)
    internal val CHILDRENENTITIES_CONNECTION_ID: ConnectionId = ConnectionId.create(ProjectModelTestEntity::class.java,
                                                                                    ProjectModelTestEntity::class.java,
                                                                                    ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                    true)
    internal val CONTENTROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(ProjectModelTestEntity::class.java,
                                                                               ContentRootTestEntity::class.java,
                                                                               ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                               true)
    private val connections = listOf<ConnectionId>(PARENTENTITY_CONNECTION_ID, CHILDRENENTITIES_CONNECTION_ID, CONTENTROOT_CONNECTION_ID)
  }

  override val info: String
    get() {
      readField("info")
      return dataSource.info
    }
  override val descriptor: Descriptor
    get() {
      readField("descriptor")
      return dataSource.descriptor
    }
  override val parentEntity: ProjectModelTestEntity?
    get() = snapshot.instrumentation.getParent(PARENTENTITY_CONNECTION_ID, this) as? ProjectModelTestEntity
  override val childrenEntities: List<ProjectModelTestEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDRENENTITIES_CONNECTION_ID, this) as? Sequence<ProjectModelTestEntity>)?.toList()
            ?: error("Children list childrenEntities not found for ProjectModelTestEntity")
  override val contentRoot: ContentRootTestEntity?
    get() = snapshot.instrumentation.getOneChild(CONTENTROOT_CONNECTION_ID, this) as? ContentRootTestEntity
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ProjectModelTestEntityData?) :
    ModifiableWorkspaceEntityBase<ProjectModelTestEntity, ProjectModelTestEntityData>(result), ProjectModelTestEntityBuilder {
    internal constructor() : this(ProjectModelTestEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isInfoInitialized()) {
        error("Field ProjectModelTestEntity#info should be initialized")
      }
      if (!getEntityData().isDescriptorInitialized()) {
        error("Field ProjectModelTestEntity#descriptor should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ProjectModelTestEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.info != dataSource.info) this.info = dataSource.info
      if (this.descriptor != dataSource.descriptor) this.descriptor = dataSource.descriptor
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var info: String
      get() = getEntityData().info
      set(value) {
        checkModificationAllowed()
        getEntityData(true).info = value
        changedProperty.add("info")
      }
    override var descriptor: Descriptor
      get() = getEntityData().descriptor
      set(value) {
        checkModificationAllowed()
        getEntityData(true).descriptor = value
        changedProperty.add("descriptor")
      }
    override var parentEntity: ProjectModelTestEntityBuilder?
      get() = getParent(PARENTENTITY_CONNECTION_ID) as? ProjectModelTestEntityBuilder?
              ?: error("parentEntity is null for ProjectModelTestEntity")
      set(value) {
        changeParentOfMany(value, PARENTENTITY_CONNECTION_ID)
        changedProperty.add("parentEntity")
      }
    override var childrenEntities: List<ProjectModelTestEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CHILDRENENTITIES_CONNECTION_ID) as List<ProjectModelTestEntityBuilder>
      set(value) {
        changeChildren(value, CHILDRENENTITIES_CONNECTION_ID)
        changedProperty.add("childrenEntities")
      }
    override var contentRoot: ContentRootTestEntityBuilder?
      get() = getChild(CONTENTROOT_CONNECTION_ID) as? ContentRootTestEntityBuilder?
      set(value) {
        changeChild(value, CONTENTROOT_CONNECTION_ID)
        changedProperty.add("contentRoot")
      }

    override fun getEntityClass(): Class<ProjectModelTestEntity> = ProjectModelTestEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ProjectModelTestEntityData : WorkspaceEntityData<ProjectModelTestEntity>() {
  lateinit var info: String
  lateinit var descriptor: Descriptor
  internal fun isInfoInitialized(): Boolean = ::info.isInitialized
  internal fun isDescriptorInitialized(): Boolean = ::descriptor.isInitialized
  override fun newInstance(): ProjectModelTestEntity = ProjectModelTestEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ProjectModelTestEntity, *> = ProjectModelTestEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.ProjectModelTestEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ProjectModelTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ProjectModelTestEntity(info, descriptor, entitySource) {
      this.parentEntity = parents.filterIsInstance<ProjectModelTestEntityBuilder>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ProjectModelTestEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.info != other.info) return false
    if (this.descriptor != other.descriptor) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ProjectModelTestEntityData
    if (this.info != other.info) return false
    if (this.descriptor != other.descriptor) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + info.hashCode()
    result = 31 * result + descriptor.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + info.hashCode()
    result = 31 * result + descriptor.hashCode()
    return result
  }
}
