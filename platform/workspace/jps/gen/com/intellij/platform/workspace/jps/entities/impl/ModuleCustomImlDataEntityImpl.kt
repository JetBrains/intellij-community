// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.platform.workspace.jps.entities.ModuleCustomImlDataEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
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
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ModuleCustomImlDataEntityImpl(private val dataSource: ModuleCustomImlDataEntityData) : ModuleCustomImlDataEntity,
                                                                                                      WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleEntity::class.java, ModuleCustomImlDataEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val connections = listOf<ConnectionId>(MODULE_CONNECTION_ID)
  }

  override val rootManagerTagCustomData: String?
    get() {
      readField("rootManagerTagCustomData")
      return dataSource.rootManagerTagCustomData
    }
  override val customModuleOptions: Map<String, String>
    get() {
      readField("customModuleOptions")
      return dataSource.customModuleOptions
    }
  override val module: ModuleEntity
    get() = snapshot.instrumentation.getParent(MODULE_CONNECTION_ID, this) as? ModuleEntity
            ?: error("Parent module not found for ModuleCustomImlDataEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ModuleCustomImlDataEntityData?) :
    ModifiableWorkspaceEntityBase<ModuleCustomImlDataEntity, ModuleCustomImlDataEntityData>(result), ModuleCustomImlDataEntity.Builder {
    internal constructor() : this(ModuleCustomImlDataEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isCustomModuleOptionsInitialized()) {
        error("Field ModuleCustomImlDataEntity#customModuleOptions should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(MODULE_CONNECTION_ID, this) == null) {
          error("Field ModuleCustomImlDataEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field ModuleCustomImlDataEntity#module should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ModuleCustomImlDataEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.rootManagerTagCustomData != dataSource.rootManagerTagCustomData) this.rootManagerTagCustomData =
        dataSource.rootManagerTagCustomData
      if (this.customModuleOptions != dataSource.customModuleOptions) this.customModuleOptions =
        dataSource.customModuleOptions.toMutableMap()
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var rootManagerTagCustomData: String?
      get() = getEntityData().rootManagerTagCustomData
      set(value) {
        checkModificationAllowed()
        getEntityData(true).rootManagerTagCustomData = value
        changedProperty.add("rootManagerTagCustomData")
      }
    override var customModuleOptions: Map<String, String>
      get() = getEntityData().customModuleOptions
      set(value) {
        checkModificationAllowed()
        getEntityData(true).customModuleOptions = value
        changedProperty.add("customModuleOptions")
      }
    override var module: ModuleEntityBuilder
      get() = getParent(MODULE_CONNECTION_ID) as? ModuleEntityBuilder ?: error("module is null for ModuleCustomImlDataEntity")
      set(value) {
        changeParent(value, MODULE_CONNECTION_ID)
        changedProperty.add("module")
      }

    override fun getEntityClass(): Class<ModuleCustomImlDataEntity> = ModuleCustomImlDataEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ModuleCustomImlDataEntityData : WorkspaceEntityData<ModuleCustomImlDataEntity>() {
  var rootManagerTagCustomData: String? = null
  lateinit var customModuleOptions: Map<String, String>
  internal fun isCustomModuleOptionsInitialized(): Boolean = ::customModuleOptions.isInitialized
  override fun newInstance(): ModuleCustomImlDataEntity = ModuleCustomImlDataEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ModuleCustomImlDataEntity, *> =
    ModuleCustomImlDataEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.jps.entities.ModuleCustomImlDataEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ModuleCustomImlDataEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ModuleCustomImlDataEntity(customModuleOptions, entitySource) {
      this.rootManagerTagCustomData = this@ModuleCustomImlDataEntityData.rootManagerTagCustomData
      parents.filterIsInstance<ModuleEntityBuilder>().singleOrNull()?.let { this.module = it }
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
    other as ModuleCustomImlDataEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.rootManagerTagCustomData != other.rootManagerTagCustomData) return false
    if (this.customModuleOptions != other.customModuleOptions) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ModuleCustomImlDataEntityData
    if (this.rootManagerTagCustomData != other.rootManagerTagCustomData) return false
    if (this.customModuleOptions != other.customModuleOptions) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + rootManagerTagCustomData.hashCode()
    result = 31 * result + customModuleOptions.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + rootManagerTagCustomData.hashCode()
    result = 31 * result + customModuleOptions.hashCode()
    return result
  }
}
