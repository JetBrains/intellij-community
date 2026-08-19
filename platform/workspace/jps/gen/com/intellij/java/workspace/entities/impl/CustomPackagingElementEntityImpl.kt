// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.java.workspace.entities.impl

import com.intellij.java.workspace.entities.ArtifactEntity
import com.intellij.java.workspace.entities.ArtifactEntityBuilder
import com.intellij.java.workspace.entities.CompositePackagingElementEntity
import com.intellij.java.workspace.entities.CompositePackagingElementEntityBuilder
import com.intellij.java.workspace.entities.CustomPackagingElementEntity
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
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class CustomPackagingElementEntityImpl(private val dataSource: CustomPackagingElementEntityData) : CustomPackagingElementEntity,
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
  override val typeId: String
    get() {
      readField("typeId")
      return dataSource.typeId
    }
  override val propertiesXmlTag: String
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

  internal class Builder(result: CustomPackagingElementEntityData?) :
    ModifiableWorkspaceEntityBase<CustomPackagingElementEntity, CustomPackagingElementEntityData>(result),
    CustomPackagingElementEntity.Builder {
    internal constructor() : this(CustomPackagingElementEntityData())

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
      if (!getEntityData().isTypeIdInitialized()) {
        error("Field CustomPackagingElementEntity#typeId should be initialized")
      }
      if (!getEntityData().isPropertiesXmlTagInitialized()) {
        error("Field CustomPackagingElementEntity#propertiesXmlTag should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as CustomPackagingElementEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.typeId != dataSource.typeId) this.typeId = dataSource.typeId
      if (this.propertiesXmlTag != dataSource.propertiesXmlTag) this.propertiesXmlTag = dataSource.propertiesXmlTag
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
      get() = getParent(PARENTENTITY_CONNECTION_ID) as? CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
              ?: error("parentEntity is null for PackagingElementEntity")
      set(value) {
        changeParentOfMany(value, PARENTENTITY_CONNECTION_ID)
        changedProperty.add("parentEntity")
      }
    override var artifact: ArtifactEntityBuilder?
      get() = getParent(ARTIFACT_CONNECTION_ID) as? ArtifactEntityBuilder? ?: error("artifact is null for CompositePackagingElementEntity")
      set(value) {
        changeParent(value, ARTIFACT_CONNECTION_ID)
        changedProperty.add("artifact")
      }
    override var children: List<PackagingElementEntityBuilder<out PackagingElementEntity>>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CHILDREN_CONNECTION_ID) as List<PackagingElementEntityBuilder<out PackagingElementEntity>>
      set(value) {
        changeChildren(value, CHILDREN_CONNECTION_ID)
        changedProperty.add("children")
      }
    override var typeId: String
      get() = getEntityData().typeId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).typeId = value
        changedProperty.add("typeId")
      }
    override var propertiesXmlTag: String
      get() = getEntityData().propertiesXmlTag
      set(value) {
        checkModificationAllowed()
        getEntityData(true).propertiesXmlTag = value
        changedProperty.add("propertiesXmlTag")
      }

    override fun getEntityClass(): Class<CustomPackagingElementEntity> = CustomPackagingElementEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class CustomPackagingElementEntityData : WorkspaceEntityData<CustomPackagingElementEntity>() {
  lateinit var typeId: String
  lateinit var propertiesXmlTag: String
  internal fun isTypeIdInitialized(): Boolean = ::typeId.isInitialized
  internal fun isPropertiesXmlTagInitialized(): Boolean = ::propertiesXmlTag.isInitialized
  override fun newInstance(): CustomPackagingElementEntity = CustomPackagingElementEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<CustomPackagingElementEntity, *> =
    CustomPackagingElementEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.workspace.entities.CustomPackagingElementEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return CustomPackagingElementEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return CustomPackagingElementEntity(typeId, propertiesXmlTag, entitySource) {
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
    other as CustomPackagingElementEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.typeId != other.typeId) return false
    if (this.propertiesXmlTag != other.propertiesXmlTag) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as CustomPackagingElementEntityData
    if (this.typeId != other.typeId) return false
    if (this.propertiesXmlTag != other.propertiesXmlTag) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + typeId.hashCode()
    result = 31 * result + propertiesXmlTag.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + typeId.hashCode()
    result = 31 * result + propertiesXmlTag.hashCode()
    return result
  }
}
