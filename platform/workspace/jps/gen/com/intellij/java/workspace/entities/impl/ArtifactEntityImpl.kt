// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.java.workspace.entities.impl

import com.intellij.java.workspace.entities.ArtifactEntity
import com.intellij.java.workspace.entities.ArtifactId
import com.intellij.java.workspace.entities.ArtifactOutputPackagingElementEntity
import com.intellij.java.workspace.entities.ArtifactOutputPackagingElementEntityBuilder
import com.intellij.java.workspace.entities.ArtifactPropertiesEntity
import com.intellij.java.workspace.entities.ArtifactPropertiesEntityBuilder
import com.intellij.java.workspace.entities.CompositePackagingElementEntity
import com.intellij.java.workspace.entities.CompositePackagingElementEntityBuilder
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
internal class ArtifactEntityImpl(private val dataSource: ArtifactEntityData) : ArtifactEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val ROOTELEMENT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java,
                                                                               CompositePackagingElementEntity::class.java,
                                                                               ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                               true)
    internal val CUSTOMPROPERTIES_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ArtifactEntity::class.java, ArtifactPropertiesEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java,
                                                                                                  ArtifactOutputPackagingElementEntity::class.java,
                                                                                                  ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                  true)
    private val connections =
      listOf<ConnectionId>(ROOTELEMENT_CONNECTION_ID, CUSTOMPROPERTIES_CONNECTION_ID, ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID)
  }

  override val symbolicId: ArtifactId = super.symbolicId

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }
  override val artifactType: String
    get() {
      readField("artifactType")
      return dataSource.artifactType
    }
  override val includeInProjectBuild: Boolean
    get() {
      readField("includeInProjectBuild")
      return dataSource.includeInProjectBuild
    }
  override val outputUrl: VirtualFileUrl?
    get() {
      readField("outputUrl")
      return dataSource.outputUrl
    }
  override val rootElement: CompositePackagingElementEntity?
    get() = snapshot.instrumentation.getOneChild(ROOTELEMENT_CONNECTION_ID, this) as? CompositePackagingElementEntity
  override val customProperties: List<ArtifactPropertiesEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CUSTOMPROPERTIES_CONNECTION_ID,
                                                      this) as? Sequence<ArtifactPropertiesEntity>)?.toList()
            ?: error("Children list customProperties not found for ArtifactEntity")
  override val artifactOutputPackagingElement: ArtifactOutputPackagingElementEntity?
    get() = snapshot.instrumentation.getOneChild(ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID,
                                                 this) as? ArtifactOutputPackagingElementEntity
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ArtifactEntityData?) : ModifiableWorkspaceEntityBase<ArtifactEntity, ArtifactEntityData>(result),
                                                        ArtifactEntity.Builder {
    internal constructor() : this(ArtifactEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field ArtifactEntity#name should be initialized")
      }
      if (!getEntityData().isArtifactTypeInitialized()) {
        error("Field ArtifactEntity#artifactType should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(CUSTOMPROPERTIES_CONNECTION_ID, this) == null) {
          error("Field ArtifactEntity#customProperties should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CUSTOMPROPERTIES_CONNECTION_ID)] == null) {
          error("Field ArtifactEntity#customProperties should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ArtifactEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.artifactType != dataSource.artifactType) this.artifactType = dataSource.artifactType
      if (this.includeInProjectBuild != dataSource.includeInProjectBuild) this.includeInProjectBuild = dataSource.includeInProjectBuild
      if (this.outputUrl != dataSource?.outputUrl) this.outputUrl = dataSource.outputUrl
      updateChildToParentReferences(parents)
    }

    override fun index() {
      index(this, "outputUrl", this.outputUrl)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var name: String
      get() = getEntityData().name
      set(value) {
        checkModificationAllowed()
        getEntityData(true).name = value
        changedProperty.add("name")
      }
    override var artifactType: String
      get() = getEntityData().artifactType
      set(value) {
        checkModificationAllowed()
        getEntityData(true).artifactType = value
        changedProperty.add("artifactType")
      }
    override var includeInProjectBuild: Boolean
      get() = getEntityData().includeInProjectBuild
      set(value) {
        checkModificationAllowed()
        getEntityData(true).includeInProjectBuild = value
        changedProperty.add("includeInProjectBuild")
      }
    override var outputUrl: VirtualFileUrl?
      get() = getEntityData().outputUrl
      set(value) {
        checkModificationAllowed()
        getEntityData(true).outputUrl = value
        changedProperty.add("outputUrl")
        val _diff = diff
        if (_diff != null) index(this, "outputUrl", value)
      }
    override var rootElement: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
      get() = getChild(ROOTELEMENT_CONNECTION_ID) as? CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
      set(value) {
        changeChild(value, ROOTELEMENT_CONNECTION_ID)
        changedProperty.add("rootElement")
      }
    override var customProperties: List<ArtifactPropertiesEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CUSTOMPROPERTIES_CONNECTION_ID) as List<ArtifactPropertiesEntityBuilder>
      set(value) {
        changeChildren(value, CUSTOMPROPERTIES_CONNECTION_ID)
        changedProperty.add("customProperties")
      }
    override var artifactOutputPackagingElement: ArtifactOutputPackagingElementEntityBuilder?
      get() = getChild(ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID) as? ArtifactOutputPackagingElementEntityBuilder?
      set(value) {
        changeChild(value, ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID)
        changedProperty.add("artifactOutputPackagingElement")
      }

    override fun getEntityClass(): Class<ArtifactEntity> = ArtifactEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ArtifactEntityData : WorkspaceEntityData<ArtifactEntity>() {
  lateinit var name: String
  lateinit var artifactType: String
  var includeInProjectBuild: Boolean = false
  var outputUrl: VirtualFileUrl? = null
  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  internal fun isArtifactTypeInitialized(): Boolean = ::artifactType.isInitialized
  override fun newInstance(): ArtifactEntity = ArtifactEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ArtifactEntity, *> = ArtifactEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.workspace.entities.ArtifactEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ArtifactEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ArtifactEntity(name, artifactType, includeInProjectBuild, entitySource) {
      this.outputUrl = this@ArtifactEntityData.outputUrl
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ArtifactEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.name != other.name) return false
    if (this.artifactType != other.artifactType) return false
    if (this.includeInProjectBuild != other.includeInProjectBuild) return false
    if (this.outputUrl != other.outputUrl) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ArtifactEntityData
    if (this.name != other.name) return false
    if (this.artifactType != other.artifactType) return false
    if (this.includeInProjectBuild != other.includeInProjectBuild) return false
    if (this.outputUrl != other.outputUrl) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + artifactType.hashCode()
    result = 31 * result + includeInProjectBuild.hashCode()
    result = 31 * result + outputUrl.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + artifactType.hashCode()
    result = 31 * result + includeInProjectBuild.hashCode()
    result = 31 * result + outputUrl.hashCode()
    return result
  }
}
