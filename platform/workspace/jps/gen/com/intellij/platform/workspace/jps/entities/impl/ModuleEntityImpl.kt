// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ContentRootEntityBuilder
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.FacetEntityBuilder
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.ModuleTypeId
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.SdkId
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
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ModuleEntityImpl(private val dataSource: ModuleEntityData) : ModuleEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CONTENTROOTS_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleEntity::class.java, ContentRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val FACETS_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleEntity::class.java, FacetEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(CONTENTROOTS_CONNECTION_ID, FACETS_CONNECTION_ID)
  }

  override val symbolicId: ModuleId = super.symbolicId

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }
  override val type: ModuleTypeId?
    get() {
      readField("type")
      return dataSource.type
    }
  override val dependencies: List<ModuleDependencyItem>
    get() {
      readField("dependencies")
      return dataSource.dependencies
    }
  override val contentRoots: List<ContentRootEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CONTENTROOTS_CONNECTION_ID, this) as? Sequence<ContentRootEntity>)?.toList() ?: error(
      "Children list contentRoots not found for ModuleEntity")
  override val facets: List<FacetEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(FACETS_CONNECTION_ID, this) as? Sequence<FacetEntity>)?.toList()
            ?: error("Children list facets not found for ModuleEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ModuleEntityData?) : ModifiableWorkspaceEntityBase<ModuleEntity, ModuleEntityData>(result),
                                                      ModuleEntity.Builder {
    internal constructor() : this(ModuleEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field ModuleEntity#name should be initialized")
      }
      if (!getEntityData().isDependenciesInitialized()) {
        error("Field ModuleEntity#dependencies should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_dependencies = getEntityData().dependencies
      if (collection_dependencies is MutableWorkspaceList<*>) {
        collection_dependencies.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ModuleEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.type != dataSource.type) this.type = dataSource.type
      if (this.dependencies != dataSource.dependencies) this.dependencies = dataSource.dependencies.toMutableList()
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
    override var type: ModuleTypeId?
      get() = getEntityData().type
      set(value) {
        checkModificationAllowed()
        getEntityData(true).type = value
        changedProperty.add("type")
      }
    private val dependenciesUpdater: (value: List<ModuleDependencyItem>) -> Unit = { value ->

      changedProperty.add("dependencies")
    }
    override var dependencies: MutableList<ModuleDependencyItem>
      get() {
        val collection_dependencies = getEntityData().dependencies
        if (collection_dependencies !is MutableWorkspaceList) return collection_dependencies
        if (diff == null || modifiable.get()) {
          collection_dependencies.setModificationUpdateAction(dependenciesUpdater)
        }
        else {
          collection_dependencies.cleanModificationUpdateAction()
        }
        return collection_dependencies
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).dependencies = value
        dependenciesUpdater.invoke(value)
      }
    override var contentRoots: List<ContentRootEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CONTENTROOTS_CONNECTION_ID) as List<ContentRootEntityBuilder>
      set(value) {
        changeChildren(value, CONTENTROOTS_CONNECTION_ID)
        changedProperty.add("contentRoots")
      }
    override var facets: List<FacetEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(FACETS_CONNECTION_ID) as List<FacetEntityBuilder>
      set(value) {
        changeChildren(value, FACETS_CONNECTION_ID)
        changedProperty.add("facets")
      }

    override fun getEntityClass(): Class<ModuleEntity> = ModuleEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ModuleEntityData : WorkspaceEntityData<ModuleEntity>(), SoftLinkable {
  lateinit var name: String
  var type: ModuleTypeId? = null
  lateinit var dependencies: MutableList<ModuleDependencyItem>
  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  internal fun isDependenciesInitialized(): Boolean = ::dependencies.isInitialized
  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    for (item in dependencies) {
      when (item) {
        is InheritedSdkDependency -> {
        }
        is LibraryDependency -> {
          result.add(item.library)
        }
        is ModuleDependency -> {
          result.add(item.module)
        }
        is ModuleSourceDependency -> {
        }
        is SdkDependency -> {
          result.add(item.sdk)
        }
      }
    }
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    for (item in dependencies) {
      when (item) {
        is InheritedSdkDependency -> {
        }
        is LibraryDependency -> {
          index.index(this, item.library)
        }
        is ModuleDependency -> {
          index.index(this, item.module)
        }
        is ModuleSourceDependency -> {
        }
        is SdkDependency -> {
          index.index(this, item.sdk)
        }
      }
    }
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    val mutablePreviousSet = HashSet(prev)
    for (item in dependencies) {
      when (item) {
        is InheritedSdkDependency -> {
        }
        is LibraryDependency -> {
          val removedItem_item_library = mutablePreviousSet.remove(item.library)
          if (!removedItem_item_library) {
            index.index(this, item.library)
          }
        }
        is ModuleDependency -> {
          val removedItem_item_module = mutablePreviousSet.remove(item.module)
          if (!removedItem_item_module) {
            index.index(this, item.module)
          }
        }
        is ModuleSourceDependency -> {
        }
        is SdkDependency -> {
          val removedItem_item_sdk = mutablePreviousSet.remove(item.sdk)
          if (!removedItem_item_sdk) {
            index.index(this, item.sdk)
          }
        }
      }
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val dependencies_data = dependencies.map {
      val _it = it
      val res_it = when (_it) {
        is InheritedSdkDependency -> {
          _it
        }
        is LibraryDependency -> {
          val _it_library_data = if (_it.library == oldLink) {
            changed = true
            newLink as LibraryId
          }
          else {
            null
          }
          var _it_data = _it
          if (_it_library_data != null) {
            _it_data = _it_data.copy(library = _it_library_data)
          }
          _it_data
        }
        is ModuleDependency -> {
          val _it_module_data = if (_it.module == oldLink) {
            changed = true
            newLink as ModuleId
          }
          else {
            null
          }
          var _it_data = _it
          if (_it_module_data != null) {
            _it_data = _it_data.copy(module = _it_module_data)
          }
          _it_data
        }
        is ModuleSourceDependency -> {
          _it
        }
        is SdkDependency -> {
          val _it_sdk_data = if (_it.sdk == oldLink) {
            changed = true
            newLink as SdkId
          }
          else {
            null
          }
          var _it_data = _it
          if (_it_sdk_data != null) {
            _it_data = _it_data.copy(sdk = _it_sdk_data)
          }
          _it_data
        }
      }
      if (res_it != null) {
        res_it
      }
      else {
        it
      }
    }

    if (dependencies_data != null) {
      dependencies = dependencies_data as MutableList<ModuleDependencyItem>
    }
    return changed
  }

  override fun newInstance(): ModuleEntity = ModuleEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ModuleEntity, *> = ModuleEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.jps.entities.ModuleEntity") as EntityMetadata
  }

  override fun clone(): ModuleEntityData {
    val clonedEntity = super.clone()
    clonedEntity as ModuleEntityData
    clonedEntity.dependencies = clonedEntity.dependencies.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ModuleEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ModuleEntity(name, dependencies, entitySource) {
      this.type = this@ModuleEntityData.type
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ModuleEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.name != other.name) return false
    if (this.type != other.type) return false
    if (this.dependencies != other.dependencies) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ModuleEntityData
    if (this.name != other.name) return false
    if (this.type != other.type) return false
    if (this.dependencies != other.dependencies) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + type.hashCode()
    result = 31 * result + dependencies.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + type.hashCode()
    result = 31 * result + dependencies.hashCode()
    return result
  }
}
