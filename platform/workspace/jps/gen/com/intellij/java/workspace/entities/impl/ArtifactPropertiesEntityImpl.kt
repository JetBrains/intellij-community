// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.java.workspace.entities.impl

import com.intellij.java.workspace.entities.ArtifactEntity
import com.intellij.java.workspace.entities.ArtifactEntityBuilder
import com.intellij.java.workspace.entities.ArtifactPropertiesEntity
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

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ArtifactPropertiesEntityImpl(private val dataSource: ArtifactPropertiesEntityData) : ArtifactPropertiesEntity,
                                                                                                    WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val ARTIFACT_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ArtifactEntity::class.java, ArtifactPropertiesEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(ARTIFACT_CONNECTION_ID)
  }

  override val artifact: ArtifactEntity
    get() = snapshot.instrumentation.getParent(ARTIFACT_CONNECTION_ID, this) as? ArtifactEntity
            ?: error("Parent artifact not found for ArtifactPropertiesEntity")
  override val providerType: String
    get() {
      readField("providerType")
      return dataSource.providerType
    }
  override val propertiesXmlTag: String?
    get() {
      readField("propertiesXmlTag")
      return dataSource.propertiesXmlTag
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ArtifactPropertiesEntityData?) :
    ModifiableWorkspaceEntityBase<ArtifactPropertiesEntity, ArtifactPropertiesEntityData>(result), ArtifactPropertiesEntity.Builder {
    internal constructor() : this(ArtifactPropertiesEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(ARTIFACT_CONNECTION_ID, this) == null) {
          error("Field ArtifactPropertiesEntity#artifact should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, ARTIFACT_CONNECTION_ID)] == null) {
          error("Field ArtifactPropertiesEntity#artifact should be initialized")
        }
      }
      if (!getEntityData().isProviderTypeInitialized()) {
        error("Field ArtifactPropertiesEntity#providerType should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ArtifactPropertiesEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.providerType != dataSource.providerType) this.providerType = dataSource.providerType
      if (this.propertiesXmlTag != dataSource?.propertiesXmlTag) this.propertiesXmlTag = dataSource.propertiesXmlTag
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var artifact: ArtifactEntityBuilder
      get() = getParent(ARTIFACT_CONNECTION_ID) as? ArtifactEntityBuilder ?: error("artifact is null for ArtifactPropertiesEntity")
      set(value) {
        changeParentOfMany(value, ARTIFACT_CONNECTION_ID)
        changedProperty.add("artifact")
      }
    override var providerType: String
      get() = getEntityData().providerType
      set(value) {
        checkModificationAllowed()
        getEntityData(true).providerType = value
        changedProperty.add("providerType")
      }
    override var propertiesXmlTag: String?
      get() = getEntityData().propertiesXmlTag
      set(value) {
        checkModificationAllowed()
        getEntityData(true).propertiesXmlTag = value
        changedProperty.add("propertiesXmlTag")
      }

    override fun getEntityClass(): Class<ArtifactPropertiesEntity> = ArtifactPropertiesEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ArtifactPropertiesEntityData : WorkspaceEntityData<ArtifactPropertiesEntity>() {
  lateinit var providerType: String
  var propertiesXmlTag: String? = null
  internal fun isProviderTypeInitialized(): Boolean = ::providerType.isInitialized
  override fun newInstance(): ArtifactPropertiesEntity = ArtifactPropertiesEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ArtifactPropertiesEntity, *> = ArtifactPropertiesEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.workspace.entities.ArtifactPropertiesEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ArtifactPropertiesEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ArtifactPropertiesEntity(providerType, entitySource) {
      this.propertiesXmlTag = this@ArtifactPropertiesEntityData.propertiesXmlTag
      parents.filterIsInstance<ArtifactEntityBuilder>().singleOrNull()?.let { this.artifact = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ArtifactEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ArtifactPropertiesEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.providerType != other.providerType) return false
    if (this.propertiesXmlTag != other.propertiesXmlTag) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ArtifactPropertiesEntityData
    if (this.providerType != other.providerType) return false
    if (this.propertiesXmlTag != other.propertiesXmlTag) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + providerType.hashCode()
    result = 31 * result + propertiesXmlTag.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + providerType.hashCode()
    result = 31 * result + propertiesXmlTag.hashCode()
    return result
  }
}
