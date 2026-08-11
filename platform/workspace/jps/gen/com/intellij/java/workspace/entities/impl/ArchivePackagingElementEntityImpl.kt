// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.java.workspace.entities.impl

import com.intellij.java.workspace.entities.ArchivePackagingElementEntity
import com.intellij.java.workspace.entities.ArtifactEntity
import com.intellij.java.workspace.entities.ArtifactEntityBuilder
import com.intellij.java.workspace.entities.CompositePackagingElementEntity
import com.intellij.java.workspace.entities.CompositePackagingElementEntityBuilder
import com.intellij.java.workspace.entities.PackagingElementEntity
import com.intellij.java.workspace.entities.PackagingElementEntityBuilder
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
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ArchivePackagingElementEntityImpl(private val dataSource: ArchivePackagingElementEntityData) : ArchivePackagingElementEntity,
                                                                                                              WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java,
                                                                                PackagingElementEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                true)
    internal val ARTIFACT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java,
                                                                            CompositePackagingElementEntity::class.java,
                                                                            ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                            true)
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java,
                                                                            PackagingElementEntity::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                            true)
    private val connections = listOf<ConnectionId>(PARENTENTITY_CONNECTION_ID, ARTIFACT_CONNECTION_ID, CHILDREN_CONNECTION_ID)
  }

  override val parentEntity: CompositePackagingElementEntity?
    get() = snapshot.instrumentation.getParent(PARENTENTITY_CONNECTION_ID, this) as? CompositePackagingElementEntity
  override val artifact: ArtifactEntity?
    get() = snapshot.instrumentation.getParent(ARTIFACT_CONNECTION_ID, this) as? ArtifactEntity
  override val children: List<PackagingElementEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<PackagingElementEntity>)?.toList()
            ?: error("Children list children not found for CompositePackagingElementEntity")
  override val fileName: String
    get() {
      readField("fileName")
      return dataSource.fileName
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ArchivePackagingElementEntityData?) :
    ModifiableWorkspaceEntityBase<ArchivePackagingElementEntity, ArchivePackagingElementEntityData>(result),
    ArchivePackagingElementEntity.Builder {
    internal constructor() : this(ArchivePackagingElementEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field CompositePackagingElementEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field CompositePackagingElementEntity#children should be initialized")
        }
      }
      if (!getEntityData().isFileNameInitialized()) {
        error("Field ArchivePackagingElementEntity#fileName should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ArchivePackagingElementEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.fileName != dataSource.fileName) this.fileName = dataSource.fileName
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
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENTENTITY_CONNECTION_ID,
                                                                           this) as? CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>)
          ?: (this.entityLinks[EntityLink(false,
                                          PARENTENTITY_CONNECTION_ID)] as? CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>)
        }
        else {
          (this.entityLinks[EntityLink(false,
                                       PARENTENTITY_CONNECTION_ID)] as? CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>)
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
// Setting backref of the list
          @Suppress("UNCHECKED_CAST")
          val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
          value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(PARENTENTITY_CONNECTION_ID, value, this)
        }
        else {
// Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
          }
          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("parentEntity")
      }
    override var artifact: ArtifactEntityBuilder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(ARTIFACT_CONNECTION_ID, this) as? ArtifactEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, ARTIFACT_CONNECTION_ID)] as? ArtifactEntityBuilder)
        }
        else {
          (this.entityLinks[EntityLink(false, ARTIFACT_CONNECTION_ID)] as? ArtifactEntityBuilder)
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          value.entityLinks[EntityLink(true, ARTIFACT_CONNECTION_ID)] = this
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(ARTIFACT_CONNECTION_ID, value, this)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, ARTIFACT_CONNECTION_ID)] = this
          }
          this.entityLinks[EntityLink(false, ARTIFACT_CONNECTION_ID)] = value
        }
        changedProperty.add("artifact")
      }
    override var children: List<PackagingElementEntityBuilder<out PackagingElementEntity>>
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this)!!
            .toList() as List<PackagingElementEntityBuilder<out PackagingElementEntity>>) + (this.entityLinks[EntityLink(true,
                                                                                                                         CHILDREN_CONNECTION_ID)] as? List<PackagingElementEntityBuilder<out PackagingElementEntity>>
                                                                                             ?: emptyList())
        }
        else {
          @Suppress("UNCHECKED_CAST")
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as List<PackagingElementEntityBuilder<out PackagingElementEntity>>
          ?: emptyList()
        }
      }
      set(value) {
// Set list of ref types for abstract entities
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
// Backref setup before adding to store an abstract entity
              item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
              @Suppress("UNCHECKED_CAST")
              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.instrumentation.replaceChildren(CHILDREN_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
            }
          }
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] = value
        }
        changedProperty.add("children")
      }
    override var fileName: String
      get() = getEntityData().fileName
      set(value) {
        checkModificationAllowed()
        getEntityData(true).fileName = value
        changedProperty.add("fileName")
      }

    override fun getEntityClass(): Class<ArchivePackagingElementEntity> = ArchivePackagingElementEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ArchivePackagingElementEntityData : WorkspaceEntityData<ArchivePackagingElementEntity>() {
  lateinit var fileName: String
  internal fun isFileNameInitialized(): Boolean = ::fileName.isInitialized
  override fun newInstance(): ArchivePackagingElementEntity = ArchivePackagingElementEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ArchivePackagingElementEntity, *> =
    ArchivePackagingElementEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.workspace.entities.ArchivePackagingElementEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ArchivePackagingElementEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ArchivePackagingElementEntity(fileName, entitySource) {
      this.parentEntity =
        parents.filterIsInstance<CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>>().singleOrNull()
      this.artifact = parents.filterIsInstance<ArtifactEntityBuilder>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ArchivePackagingElementEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.fileName != other.fileName) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ArchivePackagingElementEntityData
    if (this.fileName != other.fileName) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + fileName.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + fileName.hashCode()
    return result
  }
}
