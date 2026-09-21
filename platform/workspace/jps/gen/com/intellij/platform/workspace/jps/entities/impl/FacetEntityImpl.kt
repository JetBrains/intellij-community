// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.FacetEntityBuilder
import com.intellij.platform.workspace.jps.entities.FacetEntityTypeId
import com.intellij.platform.workspace.jps.entities.FacetId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSettingsFacetBridgeEntity
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

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class FacetEntityImpl(private val dataSource: FacetEntityData) : FacetEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java,
                                                                          ModuleSettingsFacetBridgeEntity::class.java,
                                                                          ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                          false)
    internal val UNDERLYINGFACET_CONNECTION_ID: ConnectionId =
      ConnectionId.create(FacetEntity::class.java, FacetEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
    private val connections = listOf<ConnectionId>(MODULE_CONNECTION_ID, UNDERLYINGFACET_CONNECTION_ID)
  }

  override val symbolicId: FacetId = FacetId(dataSource.name, dataSource.typeId, dataSource.moduleSymbolicId_Synthetic)

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }
  override val module: ModuleEntity
    get() = snapshot.instrumentation.getParent(MODULE_CONNECTION_ID, this) as? ModuleEntity
            ?: error("Parent module not found for ModuleSettingsFacetBridgeEntity")
  override val typeId: FacetEntityTypeId
    get() {
      readField("typeId")
      return dataSource.typeId
    }
  override val configurationXmlTag: String?
    get() {
      readField("configurationXmlTag")
      return dataSource.configurationXmlTag
    }
  override val underlyingFacet: FacetEntity?
    get() = snapshot.instrumentation.getParent(UNDERLYINGFACET_CONNECTION_ID, this) as? FacetEntity
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: FacetEntityData?) : ModifiableWorkspaceEntityBase<FacetEntity, FacetEntityData>(result),
                                                     FacetEntity.Builder {
    internal constructor() : this(FacetEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field ModuleSettingsFacetBridgeEntity#name should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(MODULE_CONNECTION_ID, this) == null) {
          error("Field ModuleSettingsFacetBridgeEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field ModuleSettingsFacetBridgeEntity#module should be initialized")
        }
      }
      if (!getEntityData().isTypeIdInitialized()) {
        error("Field FacetEntity#typeId should be initialized")
      }
      if (!getEntityData().isModuleSymbolicId_SyntheticInitialized()) {
        error("Field FacetEntity#module should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as FacetEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.typeId != dataSource.typeId) this.typeId = dataSource.typeId
      if (this.configurationXmlTag != dataSource.configurationXmlTag) this.configurationXmlTag = dataSource.configurationXmlTag
      updateChildToParentReferences(parents)
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
    override var module: ModuleEntityBuilder
      get() = getParent(MODULE_CONNECTION_ID) as? ModuleEntityBuilder ?: error("module is null for ModuleSettingsFacetBridgeEntity")
      set(value) {
        changeParentOfMany(value, MODULE_CONNECTION_ID)
        changedProperty.add("module")
        updateSymbolicId(value, MODULE_CONNECTION_ID)
      }
    override var typeId: FacetEntityTypeId
      get() = getEntityData().typeId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).typeId = value
        changedProperty.add("typeId")
      }
    override var configurationXmlTag: String?
      get() = getEntityData().configurationXmlTag
      set(value) {
        checkModificationAllowed()
        getEntityData(true).configurationXmlTag = value
        changedProperty.add("configurationXmlTag")
      }
    override var underlyingFacet: FacetEntityBuilder?
      get() = getParent(UNDERLYINGFACET_CONNECTION_ID) as? FacetEntityBuilder? ?: error("underlyingFacet is null for FacetEntity")
      set(value) {
        changeParentOfMany(value, UNDERLYINGFACET_CONNECTION_ID)
        changedProperty.add("underlyingFacet")
      }

    override fun getEntityClass(): Class<FacetEntity> = FacetEntity::class.java
    override fun updateSymbolicId(parent: WorkspaceEntityBuilder<*>, connectionId: ConnectionId) {
      if (connectionId == MODULE_CONNECTION_ID) {
        parent as ModuleEntityBuilder
        getEntityData(true).moduleSymbolicId_Synthetic = ModuleId(parent.name)
        changedProperty.add("moduleSymbolicId_Synthetic")
      }
    }
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class FacetEntityData : WorkspaceEntityData<FacetEntity>(), SoftLinkable {
  lateinit var name: String
  lateinit var typeId: FacetEntityTypeId
  var configurationXmlTag: String? = null
  lateinit var moduleSymbolicId_Synthetic: ModuleId
  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  internal fun isTypeIdInitialized(): Boolean = ::typeId.isInitialized
  internal fun isModuleSymbolicId_SyntheticInitialized(): Boolean = ::moduleSymbolicId_Synthetic.isInitialized
  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    result.add(moduleSymbolicId_Synthetic)
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    index.index(this, moduleSymbolicId_Synthetic)
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    val mutablePreviousSet = HashSet(prev)
    val removedItem_moduleSymbolicId_Synthetic = mutablePreviousSet.remove(moduleSymbolicId_Synthetic)
    if (!removedItem_moduleSymbolicId_Synthetic) {
      index.index(this, moduleSymbolicId_Synthetic)
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val moduleSymbolicId_Synthetic_data = if (moduleSymbolicId_Synthetic == oldLink) {
      changed = true
      newLink as ModuleId
    }
    else {
      null
    }
    if (moduleSymbolicId_Synthetic_data != null) {
      moduleSymbolicId_Synthetic = moduleSymbolicId_Synthetic_data
    }
    return changed
  }

  override fun newInstance(): FacetEntity = FacetEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<FacetEntity, *> = FacetEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.jps.entities.FacetEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return FacetEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return FacetEntity(name, typeId, entitySource) {
      this.configurationXmlTag = this@FacetEntityData.configurationXmlTag
      parents.filterIsInstance<ModuleEntityBuilder>().singleOrNull()?.let { this.module = it }
      this.underlyingFacet = parents.filterIsInstance<FacetEntityBuilder>().singleOrNull()
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
    other as FacetEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.name != other.name) return false
    if (this.typeId != other.typeId) return false
    if (this.configurationXmlTag != other.configurationXmlTag) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as FacetEntityData
    if (this.name != other.name) return false
    if (this.typeId != other.typeId) return false
    if (this.configurationXmlTag != other.configurationXmlTag) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + typeId.hashCode()
    result = 31 * result + configurationXmlTag.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + typeId.hashCode()
    result = 31 * result + configurationXmlTag.hashCode()
    return result
  }
}
