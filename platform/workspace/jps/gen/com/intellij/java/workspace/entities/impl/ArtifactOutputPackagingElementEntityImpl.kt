// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.java.workspace.entities.impl

import com.intellij.java.workspace.entities.ArtifactId
import com.intellij.java.workspace.entities.ArtifactOutputPackagingElementEntity
import com.intellij.java.workspace.entities.CompositePackagingElementEntity
import com.intellij.java.workspace.entities.CompositePackagingElementEntityBuilder
import com.intellij.java.workspace.entities.PackagingElementEntity
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ArtifactOutputPackagingElementEntityImpl(private val dataSource: ArtifactOutputPackagingElementEntityData) :
  ArtifactOutputPackagingElementEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java,
                                                                                PackagingElementEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                true)
    private val connections = listOf<ConnectionId>(PARENTENTITY_CONNECTION_ID)
  }

  override val parentEntity: CompositePackagingElementEntity?
    get() = snapshot.instrumentation.getParent(PARENTENTITY_CONNECTION_ID, this) as? CompositePackagingElementEntity
  override val artifact: ArtifactId?
    get() {
      readField("artifact")
      return dataSource.artifact
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ArtifactOutputPackagingElementEntityData?) :
    ModifiableWorkspaceEntityBase<ArtifactOutputPackagingElementEntity, ArtifactOutputPackagingElementEntityData>(result),
    ArtifactOutputPackagingElementEntity.Builder {
    internal constructor() : this(ArtifactOutputPackagingElementEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ArtifactOutputPackagingElementEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.artifact != dataSource.artifact) this.artifact = dataSource.artifact
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
    override var artifact: ArtifactId?
      get() = getEntityData().artifact
      set(value) {
        checkModificationAllowed()
        getEntityData(true).artifact = value
        changedProperty.add("artifact")
      }

    override fun getEntityClass(): Class<ArtifactOutputPackagingElementEntity> = ArtifactOutputPackagingElementEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ArtifactOutputPackagingElementEntityData : WorkspaceEntityData<ArtifactOutputPackagingElementEntity>(), SoftLinkable {
  var artifact: ArtifactId? = null
  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    val optionalLink_artifact = artifact
    if (optionalLink_artifact != null) {
      result.add(optionalLink_artifact)
    }
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    val optionalLink_artifact = artifact
    if (optionalLink_artifact != null) {
      index.index(this, optionalLink_artifact)
    }
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    val mutablePreviousSet = HashSet(prev)
    val optionalLink_artifact = artifact
    if (optionalLink_artifact != null) {
      val removedItem_optionalLink_artifact = mutablePreviousSet.remove(optionalLink_artifact)
      if (!removedItem_optionalLink_artifact) {
        index.index(this, optionalLink_artifact)
      }
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    var artifact_data_optional = if (artifact != null) {
      val artifact___data = if (artifact!! == oldLink) {
        changed = true
        newLink as ArtifactId
      }
      else {
        null
      }
      artifact___data
    }
    else {
      null
    }

    if (artifact_data_optional != null) {
      artifact = artifact_data_optional
    }
    return changed
  }

  override fun newInstance(): ArtifactOutputPackagingElementEntity = ArtifactOutputPackagingElementEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ArtifactOutputPackagingElementEntity, *> =
    ArtifactOutputPackagingElementEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.workspace.entities.ArtifactOutputPackagingElementEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ArtifactOutputPackagingElementEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ArtifactOutputPackagingElementEntity(entitySource) {
      this.artifact = this@ArtifactOutputPackagingElementEntityData.artifact
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
    other as ArtifactOutputPackagingElementEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.artifact != other.artifact) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ArtifactOutputPackagingElementEntityData
    if (this.artifact != other.artifact) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + artifact.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + artifact.hashCode()
    return result
  }
}
