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
import com.intellij.platform.workspace.storage.testEntities.entities.FacetTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.FacetTestEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntitySymbolicId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ModuleTestEntityImpl(private val dataSource: ModuleTestEntityData) : ModuleTestEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CONTENTROOTS_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleTestEntity::class.java, ContentRootTestEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val FACETS_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleTestEntity::class.java, FacetTestEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(CONTENTROOTS_CONNECTION_ID, FACETS_CONNECTION_ID)
  }

  override val symbolicId: ModuleTestEntitySymbolicId = super.symbolicId

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }
  override val contentRoots: List<ContentRootTestEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CONTENTROOTS_CONNECTION_ID, this) as? Sequence<ContentRootTestEntity>)?.toList()
            ?: error("Children list contentRoots not found for ModuleTestEntity")
  override val facets: List<FacetTestEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(FACETS_CONNECTION_ID, this) as? Sequence<FacetTestEntity>)?.toList()
            ?: error("Children list facets not found for ModuleTestEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ModuleTestEntityData?) : ModifiableWorkspaceEntityBase<ModuleTestEntity, ModuleTestEntityData>(result),
                                                          ModuleTestEntityBuilder {
    internal constructor() : this(ModuleTestEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field ModuleTestEntity#name should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ModuleTestEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
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
    override var contentRoots: List<ContentRootTestEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CONTENTROOTS_CONNECTION_ID) as List<ContentRootTestEntityBuilder>
      set(value) {
        changeChildren(value, CONTENTROOTS_CONNECTION_ID)
        changedProperty.add("contentRoots")
      }
    override var facets: List<FacetTestEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(FACETS_CONNECTION_ID) as List<FacetTestEntityBuilder>
      set(value) {
        changeChildren(value, FACETS_CONNECTION_ID)
        changedProperty.add("facets")
      }

    override fun getEntityClass(): Class<ModuleTestEntity> = ModuleTestEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ModuleTestEntityData : WorkspaceEntityData<ModuleTestEntity>() {
  lateinit var name: String
  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  override fun newInstance(): ModuleTestEntity = ModuleTestEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ModuleTestEntity, *> = ModuleTestEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ModuleTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ModuleTestEntity(name, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ModuleTestEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.name != other.name) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ModuleTestEntityData
    if (this.name != other.name) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    return result
  }
}
