// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.java.workspace.entities.impl

import com.intellij.java.workspace.entities.CompositePackagingElementEntity
import com.intellij.java.workspace.entities.CompositePackagingElementEntityBuilder
import com.intellij.java.workspace.entities.ExtractedDirectoryPackagingElementEntity
import com.intellij.java.workspace.entities.PackagingElementEntity
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
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ExtractedDirectoryPackagingElementEntityImpl(private val dataSource: ExtractedDirectoryPackagingElementEntityData) :
  ExtractedDirectoryPackagingElementEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java,
                                                                                PackagingElementEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                true)
    private val connections = listOf<ConnectionId>(PARENTENTITY_CONNECTION_ID)
  }

  override val parentEntity: CompositePackagingElementEntity?
    get() = snapshot.instrumentation.getParent(PARENTENTITY_CONNECTION_ID, this) as? CompositePackagingElementEntity
  override val filePath: VirtualFileUrl
    get() {
      readField("filePath")
      return dataSource.filePath
    }
  override val pathInArchive: String
    get() {
      readField("pathInArchive")
      return dataSource.pathInArchive
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ExtractedDirectoryPackagingElementEntityData?) :
    ModifiableWorkspaceEntityBase<ExtractedDirectoryPackagingElementEntity, ExtractedDirectoryPackagingElementEntityData>(result),
    ExtractedDirectoryPackagingElementEntity.Builder {
    internal constructor() : this(ExtractedDirectoryPackagingElementEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isFilePathInitialized()) {
        error("Field FileOrDirectoryPackagingElementEntity#filePath should be initialized")
      }
      if (!getEntityData().isPathInArchiveInitialized()) {
        error("Field ExtractedDirectoryPackagingElementEntity#pathInArchive should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ExtractedDirectoryPackagingElementEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.filePath != dataSource.filePath) this.filePath = dataSource.filePath
      if (this.pathInArchive != dataSource.pathInArchive) this.pathInArchive = dataSource.pathInArchive
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
      @Suppress("UNCHECKED_CAST")
      get() = getParent(PARENTENTITY_CONNECTION_ID) as? CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
              ?: error("parentEntity is null for PackagingElementEntity")
      set(value) {
        changeParentOfMany(value, PARENTENTITY_CONNECTION_ID)
        changedProperty.add("parentEntity")
      }
    override var filePath: VirtualFileUrl
      get() = getEntityData().filePath
      set(value) {
        checkModificationAllowed()
        getEntityData(true).filePath = value
        changedProperty.add("filePath")
        val _diff = diff
        if (_diff != null) index(this, "filePath", value)
      }
    override var pathInArchive: String
      get() = getEntityData().pathInArchive
      set(value) {
        checkModificationAllowed()
        getEntityData(true).pathInArchive = value
        changedProperty.add("pathInArchive")
      }

    override fun getEntityClass(): Class<ExtractedDirectoryPackagingElementEntity> = ExtractedDirectoryPackagingElementEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ExtractedDirectoryPackagingElementEntityData : WorkspaceEntityData<ExtractedDirectoryPackagingElementEntity>() {
  lateinit var filePath: VirtualFileUrl
  lateinit var pathInArchive: String
  internal fun isFilePathInitialized(): Boolean = ::filePath.isInitialized
  internal fun isPathInArchiveInitialized(): Boolean = ::pathInArchive.isInitialized
  override fun newInstance(): ExtractedDirectoryPackagingElementEntity = ExtractedDirectoryPackagingElementEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ExtractedDirectoryPackagingElementEntity, *> =
    ExtractedDirectoryPackagingElementEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.workspace.entities.ExtractedDirectoryPackagingElementEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ExtractedDirectoryPackagingElementEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ExtractedDirectoryPackagingElementEntity(filePath, pathInArchive, entitySource) {
      this.parentEntity =
        parents.filterIsInstance<CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ExtractedDirectoryPackagingElementEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.filePath != other.filePath) return false
    if (this.pathInArchive != other.pathInArchive) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ExtractedDirectoryPackagingElementEntityData
    if (this.filePath != other.filePath) return false
    if (this.pathInArchive != other.pathInArchive) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + filePath.hashCode()
    result = 31 * result + pathInArchive.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + filePath.hashCode()
    result = 31 * result + pathInArchive.hashCode()
    return result
  }
}
