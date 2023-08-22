// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.UsedClassesCollector
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.extractOneToOneChild
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneChildOfParent
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(2)
open class ModuleEntityImpl(val dataSource: ModuleEntityData) : ModuleEntity, WorkspaceEntityBase() {

  companion object {
    internal val CONTENTROOTS_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, ContentRootEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val CUSTOMIMLDATA_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java,
                                                                                 ModuleCustomImlDataEntity::class.java,
                                                                                 ConnectionId.ConnectionType.ONE_TO_ONE, false)
    internal val GROUPPATH_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, ModuleGroupPathEntity::class.java,
                                                                             ConnectionId.ConnectionType.ONE_TO_ONE, false)
    internal val EXMODULEOPTIONS_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java,
                                                                                   ExternalSystemModuleOptionsEntity::class.java,
                                                                                   ConnectionId.ConnectionType.ONE_TO_ONE, false)
    internal val TESTPROPERTIES_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java,
                                                                                  TestModulePropertiesEntity::class.java,
                                                                                  ConnectionId.ConnectionType.ONE_TO_ONE, false)
    internal val FACETS_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, FacetEntity::class.java,
                                                                          ConnectionId.ConnectionType.ONE_TO_MANY, false)

    val connections = listOf<ConnectionId>(
      CONTENTROOTS_CONNECTION_ID,
      CUSTOMIMLDATA_CONNECTION_ID,
      GROUPPATH_CONNECTION_ID,
      EXMODULEOPTIONS_CONNECTION_ID,
      TESTPROPERTIES_CONNECTION_ID,
      FACETS_CONNECTION_ID,
    )

  }

  override val name: String
    get() = dataSource.name

  override val type: String?
    get() = dataSource.type

  override val dependencies: List<ModuleDependencyItem>
    get() = dataSource.dependencies

  override val contentRoots: List<ContentRootEntity>
    get() = snapshot.extractOneToManyChildren<ContentRootEntity>(CONTENTROOTS_CONNECTION_ID, this)!!.toList()

  override val customImlData: ModuleCustomImlDataEntity?
    get() = snapshot.extractOneToOneChild(CUSTOMIMLDATA_CONNECTION_ID, this)

  override val groupPath: ModuleGroupPathEntity?
    get() = snapshot.extractOneToOneChild(GROUPPATH_CONNECTION_ID, this)

  override val exModuleOptions: ExternalSystemModuleOptionsEntity?
    get() = snapshot.extractOneToOneChild(EXMODULEOPTIONS_CONNECTION_ID, this)

  override val testProperties: TestModulePropertiesEntity?
    get() = snapshot.extractOneToOneChild(TESTPROPERTIES_CONNECTION_ID, this)

  override val facets: List<FacetEntity>
    get() = snapshot.extractOneToManyChildren<FacetEntity>(FACETS_CONNECTION_ID, this)!!.toList()

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: ModuleEntityData?) : ModifiableWorkspaceEntityBase<ModuleEntity, ModuleEntityData>(result), ModuleEntity.Builder {
    constructor() : this(ModuleEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ModuleEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
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
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CONTENTROOTS_CONNECTION_ID, this) == null) {
          error("Field ModuleEntity#contentRoots should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CONTENTROOTS_CONNECTION_ID)] == null) {
          error("Field ModuleEntity#contentRoots should be initialized")
        }
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(FACETS_CONNECTION_ID, this) == null) {
          error("Field ModuleEntity#facets should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, FACETS_CONNECTION_ID)] == null) {
          error("Field ModuleEntity#facets should be initialized")
        }
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
      if (this.type != dataSource?.type) this.type = dataSource.type
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

    override var type: String?
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

    // List of non-abstract referenced types
    var _contentRoots: List<ContentRootEntity>? = emptyList()
    override var contentRoots: List<ContentRootEntity>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyChildren<ContentRootEntity>(CONTENTROOTS_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(
            true, CONTENTROOTS_CONNECTION_ID)] as? List<ContentRootEntity> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CONTENTROOTS_CONNECTION_ID)] as? List<ContentRootEntity> ?: emptyList()
        }
      }
      set(value) {
        // Setter of the list of non-abstract referenced types
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
              // Backref setup before adding to store
              if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
                item_value.entityLinks[EntityLink(false, CONTENTROOTS_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToManyChildrenOfParent(CONTENTROOTS_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CONTENTROOTS_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, CONTENTROOTS_CONNECTION_ID)] = value
        }
        changedProperty.add("contentRoots")
      }

    override var customImlData: ModuleCustomImlDataEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(CUSTOMIMLDATA_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true,
                                                                                                       CUSTOMIMLDATA_CONNECTION_ID)] as? ModuleCustomImlDataEntity
        }
        else {
          this.entityLinks[EntityLink(true, CUSTOMIMLDATA_CONNECTION_ID)] as? ModuleCustomImlDataEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CUSTOMIMLDATA_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(CUSTOMIMLDATA_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CUSTOMIMLDATA_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, CUSTOMIMLDATA_CONNECTION_ID)] = value
        }
        changedProperty.add("customImlData")
      }

    override var groupPath: ModuleGroupPathEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(GROUPPATH_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true,
                                                                                                   GROUPPATH_CONNECTION_ID)] as? ModuleGroupPathEntity
        }
        else {
          this.entityLinks[EntityLink(true, GROUPPATH_CONNECTION_ID)] as? ModuleGroupPathEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, GROUPPATH_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(GROUPPATH_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, GROUPPATH_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, GROUPPATH_CONNECTION_ID)] = value
        }
        changedProperty.add("groupPath")
      }

    override var exModuleOptions: ExternalSystemModuleOptionsEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(EXMODULEOPTIONS_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true,
                                                                                                         EXMODULEOPTIONS_CONNECTION_ID)] as? ExternalSystemModuleOptionsEntity
        }
        else {
          this.entityLinks[EntityLink(true, EXMODULEOPTIONS_CONNECTION_ID)] as? ExternalSystemModuleOptionsEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, EXMODULEOPTIONS_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(EXMODULEOPTIONS_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, EXMODULEOPTIONS_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, EXMODULEOPTIONS_CONNECTION_ID)] = value
        }
        changedProperty.add("exModuleOptions")
      }

    override var testProperties: TestModulePropertiesEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(TESTPROPERTIES_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true,
                                                                                                        TESTPROPERTIES_CONNECTION_ID)] as? TestModulePropertiesEntity
        }
        else {
          this.entityLinks[EntityLink(true, TESTPROPERTIES_CONNECTION_ID)] as? TestModulePropertiesEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, TESTPROPERTIES_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(TESTPROPERTIES_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, TESTPROPERTIES_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, TESTPROPERTIES_CONNECTION_ID)] = value
        }
        changedProperty.add("testProperties")
      }

    // List of non-abstract referenced types
    var _facets: List<FacetEntity>? = emptyList()
    override var facets: List<FacetEntity>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyChildren<FacetEntity>(FACETS_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                            FACETS_CONNECTION_ID)] as? List<FacetEntity>
                                                                                                ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, FACETS_CONNECTION_ID)] as? List<FacetEntity> ?: emptyList()
        }
      }
      set(value) {
        // Setter of the list of non-abstract referenced types
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
              // Backref setup before adding to store
              if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
                item_value.entityLinks[EntityLink(false, FACETS_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToManyChildrenOfParent(FACETS_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, FACETS_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, FACETS_CONNECTION_ID)] = value
        }
        changedProperty.add("facets")
      }

    override fun getEntityClass(): Class<ModuleEntity> = ModuleEntity::class.java
  }
}

class ModuleEntityData : WorkspaceEntityData.WithCalculableSymbolicId<ModuleEntity>(), SoftLinkable {
  lateinit var name: String
  var type: String? = null
  lateinit var dependencies: MutableList<ModuleDependencyItem>

  fun isNameInitialized(): Boolean = ::name.isInitialized
  fun isDependenciesInitialized(): Boolean = ::dependencies.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    for (item in dependencies) {
      when (item) {
        is ModuleDependencyItem.Exportable -> {
          when (item) {
            is ModuleDependencyItem.Exportable.LibraryDependency -> {
              result.add(item.library)
            }
            is ModuleDependencyItem.Exportable.ModuleDependency -> {
              result.add(item.module)
            }
          }
        }
        is ModuleDependencyItem.InheritedSdkDependency -> {
        }
        is ModuleDependencyItem.ModuleSourceDependency -> {
        }
        is ModuleDependencyItem.SdkDependency -> {
        }
      }
    }
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    for (item in dependencies) {
      when (item) {
        is ModuleDependencyItem.Exportable -> {
          when (item) {
            is ModuleDependencyItem.Exportable.LibraryDependency -> {
              index.index(this, item.library)
            }
            is ModuleDependencyItem.Exportable.ModuleDependency -> {
              index.index(this, item.module)
            }
          }
        }
        is ModuleDependencyItem.InheritedSdkDependency -> {
        }
        is ModuleDependencyItem.ModuleSourceDependency -> {
        }
        is ModuleDependencyItem.SdkDependency -> {
        }
      }
    }
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    for (item in dependencies) {
      when (item) {
        is ModuleDependencyItem.Exportable -> {
          when (item) {
            is ModuleDependencyItem.Exportable.LibraryDependency -> {
              val removedItem_item_library = mutablePreviousSet.remove(item.library)
              if (!removedItem_item_library) {
                index.index(this, item.library)
              }
            }
            is ModuleDependencyItem.Exportable.ModuleDependency -> {
              val removedItem_item_module = mutablePreviousSet.remove(item.module)
              if (!removedItem_item_module) {
                index.index(this, item.module)
              }
            }
          }
        }
        is ModuleDependencyItem.InheritedSdkDependency -> {
        }
        is ModuleDependencyItem.ModuleSourceDependency -> {
        }
        is ModuleDependencyItem.SdkDependency -> {
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
        is ModuleDependencyItem.Exportable -> {
          val __it = _it
          val res__it = when (__it) {
            is ModuleDependencyItem.Exportable.LibraryDependency -> {
              val __it_library_data = if (__it.library == oldLink) {
                changed = true
                newLink as LibraryId
              }
              else {
                null
              }
              var __it_data = __it
              if (__it_library_data != null) {
                __it_data = __it_data.copy(library = __it_library_data)
              }
              __it_data
            }
            is ModuleDependencyItem.Exportable.ModuleDependency -> {
              val __it_module_data = if (__it.module == oldLink) {
                changed = true
                newLink as ModuleId
              }
              else {
                null
              }
              var __it_data = __it
              if (__it_module_data != null) {
                __it_data = __it_data.copy(module = __it_module_data)
              }
              __it_data
            }
          }
          res__it
        }
        is ModuleDependencyItem.InheritedSdkDependency -> {
          _it
        }
        is ModuleDependencyItem.ModuleSourceDependency -> {
          _it
        }
        is ModuleDependencyItem.SdkDependency -> {
          _it
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

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ModuleEntity> {
    val modifiable = ModuleEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ModuleEntity {
    return getCached(snapshot) {
      val entity = ModuleEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun clone(): ModuleEntityData {
    val clonedEntity = super.clone()
    clonedEntity as ModuleEntityData
    clonedEntity.dependencies = clonedEntity.dependencies.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun symbolicId(): SymbolicEntityId<*> {
    return ModuleId(name)
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ModuleEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
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

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.add(ModuleDependencyItem.SdkDependency::class.java)
    collector.add(ModuleDependencyItem.Exportable::class.java)
    collector.add(ModuleDependencyItem::class.java)
    collector.add(LibraryTableId.ModuleLibraryTableId::class.java)
    collector.add(ModuleDependencyItem.Exportable.LibraryDependency::class.java)
    collector.add(LibraryId::class.java)
    collector.add(LibraryTableId::class.java)
    collector.add(ModuleDependencyItem.DependencyScope::class.java)
    collector.add(LibraryTableId.GlobalLibraryTableId::class.java)
    collector.add(ModuleDependencyItem.Exportable.ModuleDependency::class.java)
    collector.add(ModuleId::class.java)
    collector.addObject(ModuleDependencyItem.ModuleSourceDependency::class.java)
    collector.addObject(LibraryTableId.ProjectLibraryTableId::class.java)
    collector.addObject(ModuleDependencyItem.InheritedSdkDependency::class.java)
    this.dependencies?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
