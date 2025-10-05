// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules.impl

import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class KotlinScriptEntityImpl(private val dataSource: KotlinScriptEntityData) : KotlinScriptEntity, WorkspaceEntityBase(
  dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val virtualFileUrl: VirtualFileUrl
    get() {
      readField("virtualFileUrl")
      return dataSource.virtualFileUrl
    }

  override val dependencies: List<KotlinScriptLibraryEntityId>
    get() {
      readField("dependencies")
      return dataSource.dependencies
    }

  override val sdk: ModuleDependencyItem
    get() {
      readField("sdk")
      return dataSource.sdk
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: KotlinScriptEntityData?) : ModifiableWorkspaceEntityBase<KotlinScriptEntity, KotlinScriptEntityData>(
    result), KotlinScriptEntity.Builder {
    internal constructor() : this(KotlinScriptEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity KotlinScriptEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "virtualFileUrl", this.virtualFileUrl)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isVirtualFileUrlInitialized()) {
        error("Field KotlinScriptEntity#virtualFileUrl should be initialized")
      }
      if (!getEntityData().isDependenciesInitialized()) {
        error("Field KotlinScriptEntity#dependencies should be initialized")
      }
      if (!getEntityData().isSdkInitialized()) {
        error("Field KotlinScriptEntity#sdk should be initialized")
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
      dataSource as KotlinScriptEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.virtualFileUrl != dataSource.virtualFileUrl) this.virtualFileUrl = dataSource.virtualFileUrl
      if (this.dependencies != dataSource.dependencies) this.dependencies = dataSource.dependencies.toMutableList()
      if (this.sdk != dataSource.sdk) this.sdk = dataSource.sdk
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var virtualFileUrl: VirtualFileUrl
      get() = getEntityData().virtualFileUrl
      set(value) {
        checkModificationAllowed()
        getEntityData(true).virtualFileUrl = value
        changedProperty.add("virtualFileUrl")
        val _diff = diff
        if (_diff != null) index(this, "virtualFileUrl", value)
      }

    private val dependenciesUpdater: (value: List<KotlinScriptLibraryEntityId>) -> Unit = { value ->

      changedProperty.add("dependencies")
    }
    override var dependencies: MutableList<KotlinScriptLibraryEntityId>
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

    override var sdk: ModuleDependencyItem
      get() = getEntityData().sdk
      set(value) {
        checkModificationAllowed()
        getEntityData(true).sdk = value
        changedProperty.add("sdk")

      }

    override fun getEntityClass(): Class<KotlinScriptEntity> = KotlinScriptEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class KotlinScriptEntityData : WorkspaceEntityData<KotlinScriptEntity>(), SoftLinkable {
  lateinit var virtualFileUrl: VirtualFileUrl
  lateinit var dependencies: MutableList<KotlinScriptLibraryEntityId>
  lateinit var sdk: ModuleDependencyItem

  internal fun isVirtualFileUrlInitialized(): Boolean = ::virtualFileUrl.isInitialized
  internal fun isDependenciesInitialized(): Boolean = ::dependencies.isInitialized
  internal fun isSdkInitialized(): Boolean = ::sdk.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    for (item in dependencies) {
      result.add(item)
    }
    val _sdk = sdk
    when (_sdk) {
      is InheritedSdkDependency -> {
      }
      is LibraryDependency -> {
        result.add(_sdk.library)
      }
      is ModuleDependency -> {
        result.add(_sdk.module)
      }
      is ModuleSourceDependency -> {
      }
      is SdkDependency -> {
        result.add(_sdk.sdk)
      }
    }
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    for (item in dependencies) {
      index.index(this, item)
    }
    val _sdk = sdk
    when (_sdk) {
      is InheritedSdkDependency -> {
      }
      is LibraryDependency -> {
        index.index(this, _sdk.library)
      }
      is ModuleDependency -> {
        index.index(this, _sdk.module)
      }
      is ModuleSourceDependency -> {
      }
      is SdkDependency -> {
        index.index(this, _sdk.sdk)
      }
    }
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    for (item in dependencies) {
      val removedItem_item = mutablePreviousSet.remove(item)
      if (!removedItem_item) {
        index.index(this, item)
      }
    }
    val _sdk = sdk
    when (_sdk) {
      is InheritedSdkDependency -> {
      }
      is LibraryDependency -> {
        val removedItem__sdk_library = mutablePreviousSet.remove(_sdk.library)
        if (!removedItem__sdk_library) {
          index.index(this, _sdk.library)
        }
      }
      is ModuleDependency -> {
        val removedItem__sdk_module = mutablePreviousSet.remove(_sdk.module)
        if (!removedItem__sdk_module) {
          index.index(this, _sdk.module)
        }
      }
      is ModuleSourceDependency -> {
      }
      is SdkDependency -> {
        val removedItem__sdk_sdk = mutablePreviousSet.remove(_sdk.sdk)
        if (!removedItem__sdk_sdk) {
          index.index(this, _sdk.sdk)
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
      val it_data = if (it == oldLink) {
        changed = true
        newLink as KotlinScriptLibraryEntityId
      }
      else {
        null
      }
      if (it_data != null) {
        it_data
      }
      else {
        it
      }
    }
    if (dependencies_data != null) {
      dependencies = dependencies_data as MutableList<KotlinScriptLibraryEntityId>
    }
    val _sdk = sdk
    val res_sdk = when (_sdk) {
      is InheritedSdkDependency -> {
        _sdk
      }
      is LibraryDependency -> {
        val _sdk_library_data = if (_sdk.library == oldLink) {
          changed = true
          newLink as LibraryId
        }
        else {
          null
        }
        var _sdk_data = _sdk
        if (_sdk_library_data != null) {
          _sdk_data = _sdk_data.copy(library = _sdk_library_data)
        }
        _sdk_data
      }
      is ModuleDependency -> {
        val _sdk_module_data = if (_sdk.module == oldLink) {
          changed = true
          newLink as ModuleId
        }
        else {
          null
        }
        var _sdk_data = _sdk
        if (_sdk_module_data != null) {
          _sdk_data = _sdk_data.copy(module = _sdk_module_data)
        }
        _sdk_data
      }
      is ModuleSourceDependency -> {
        _sdk
      }
      is SdkDependency -> {
        val _sdk_sdk_data = if (_sdk.sdk == oldLink) {
          changed = true
          newLink as SdkId
        }
        else {
          null
        }
        var _sdk_data = _sdk
        if (_sdk_sdk_data != null) {
          _sdk_data = _sdk_data.copy(sdk = _sdk_sdk_data)
        }
        _sdk_data
      }
    }
    if (res_sdk != null) {
      sdk = res_sdk
    }
    return changed
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<KotlinScriptEntity> {
    val modifiable = KotlinScriptEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): KotlinScriptEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = KotlinScriptEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity") as EntityMetadata
  }

  override fun clone(): KotlinScriptEntityData {
    val clonedEntity = super.clone()
    clonedEntity as KotlinScriptEntityData
    clonedEntity.dependencies = clonedEntity.dependencies.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return KotlinScriptEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return KotlinScriptEntity(virtualFileUrl, dependencies, sdk, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as KotlinScriptEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.virtualFileUrl != other.virtualFileUrl) return false
    if (this.dependencies != other.dependencies) return false
    if (this.sdk != other.sdk) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as KotlinScriptEntityData

    if (this.virtualFileUrl != other.virtualFileUrl) return false
    if (this.dependencies != other.dependencies) return false
    if (this.sdk != other.sdk) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + virtualFileUrl.hashCode()
    result = 31 * result + dependencies.hashCode()
    result = 31 * result + sdk.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + virtualFileUrl.hashCode()
    result = 31 * result + dependencies.hashCode()
    result = 31 * result + sdk.hashCode()
    return result
  }
}
