// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleImlFileEntity
import com.intellij.platform.workspace.jps.entities.ModuleImlFileEntityBuilder
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
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ModuleImlFileEntityImpl(private val dataSource: ModuleImlFileEntityData) : ModuleImlFileEntity,
                                                                                          WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleEntity::class.java, ModuleImlFileEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val connections = listOf<ConnectionId>(MODULE_CONNECTION_ID)
  }

  override val url: VirtualFileUrl
    get() {
      readField("url")
      return dataSource.url
    }
  override val module: ModuleEntity
    get() = snapshot.instrumentation.getParent(MODULE_CONNECTION_ID, this) as? ModuleEntity
            ?: error("Parent module not found for ModuleImlFileEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ModuleImlFileEntityData?) :
    ModifiableWorkspaceEntityBase<ModuleImlFileEntity, ModuleImlFileEntityData>(result), ModuleImlFileEntityBuilder {
    internal constructor() : this(ModuleImlFileEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isUrlInitialized()) {
        error("Field ModuleImlFileEntity#url should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(MODULE_CONNECTION_ID, this) == null) {
          error("Field ModuleImlFileEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field ModuleImlFileEntity#module should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ModuleImlFileEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
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
    override var url: VirtualFileUrl
      get() = getEntityData().url
      set(value) {
        checkModificationAllowed()
        getEntityData(true).url = value
        changedProperty.add("url")
        val _diff = diff
        if (_diff != null) {
          index(this, "url", value)
        }
      }
    override var module: ModuleEntityBuilder
      get() = getParent(MODULE_CONNECTION_ID) as? ModuleEntityBuilder ?: error("module is null for ModuleImlFileEntity")
      set(value) {
        changeParent(value, MODULE_CONNECTION_ID)
        changedProperty.add("module")
      }

    override fun getEntityClass(): Class<ModuleImlFileEntity> = ModuleImlFileEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ModuleImlFileEntityData : WorkspaceEntityData<ModuleImlFileEntity>() {
  lateinit var url: VirtualFileUrl
  internal fun isUrlInitialized(): Boolean = ::url.isInitialized
  override fun newInstance(): ModuleImlFileEntity = ModuleImlFileEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ModuleImlFileEntity, *> = ModuleImlFileEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.jps.entities.ModuleImlFileEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ModuleImlFileEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ModuleImlFileEntity(url, entitySource) {
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
    other as ModuleImlFileEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.url != other.url) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ModuleImlFileEntityData
    if (this.url != other.url) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + url.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + url.hashCode()
    return result
  }
}
