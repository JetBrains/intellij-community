// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.SoftLinkable
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class ModuleEntityImpl: ModuleEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val CONTENTROOTS_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, ContentRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        internal val CUSTOMIMLDATA_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, ModuleCustomImlDataEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        internal val GROUPPATH_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, ModuleGroupPathEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        internal val JAVASETTINGS_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, JavaModuleSettingsEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        internal val EXMODULEOPTIONS_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, ExternalSystemModuleOptionsEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        internal val FACETS_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, FacetEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    }
        
    @JvmField var _name: String? = null
    override val name: String
        get() = _name!!
                        
    @JvmField var _type: String? = null
    override val type: String?
        get() = _type
                        
    @JvmField var _dependencies: List<ModuleDependencyItem>? = null
    override val dependencies: List<ModuleDependencyItem>
        get() = _dependencies!!   
    
    override val contentRoots: List<ContentRootEntity>
        get() = snapshot.extractOneToManyChildren<ContentRootEntity>(CONTENTROOTS_CONNECTION_ID, this)!!.toList()
    
    override val customImlData: ModuleCustomImlDataEntity?
        get() = snapshot.extractOneToOneChild(CUSTOMIMLDATA_CONNECTION_ID, this)           
        
    override val groupPath: ModuleGroupPathEntity?
        get() = snapshot.extractOneToOneChild(GROUPPATH_CONNECTION_ID, this)           
        
    override val javaSettings: JavaModuleSettingsEntity?
        get() = snapshot.extractOneToOneChild(JAVASETTINGS_CONNECTION_ID, this)           
        
    override val exModuleOptions: ExternalSystemModuleOptionsEntity?
        get() = snapshot.extractOneToOneChild(EXMODULEOPTIONS_CONNECTION_ID, this)           
        
    override val facets: List<FacetEntity>
        get() = snapshot.extractOneToManyChildren<FacetEntity>(FACETS_CONNECTION_ID, this)!!.toList()

    class Builder(val result: ModuleEntityData?): ModifiableWorkspaceEntityBase<ModuleEntity>(), ModuleEntity.Builder {
        constructor(): this(ModuleEntityData())
        
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
            
            val __customImlData = _customImlData
            if (__customImlData != null && __customImlData is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__customImlData)
                applyRef(CUSTOMIMLDATA_CONNECTION_ID, __customImlData)
                this._customImlData = null
            }
            val __groupPath = _groupPath
            if (__groupPath != null && __groupPath is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__groupPath)
                applyRef(GROUPPATH_CONNECTION_ID, __groupPath)
                this._groupPath = null
            }
            val __javaSettings = _javaSettings
            if (__javaSettings != null && __javaSettings is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__javaSettings)
                applyRef(JAVASETTINGS_CONNECTION_ID, __javaSettings)
                this._javaSettings = null
            }
            val __exModuleOptions = _exModuleOptions
            if (__exModuleOptions != null && __exModuleOptions is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__exModuleOptions)
                applyRef(EXMODULEOPTIONS_CONNECTION_ID, __exModuleOptions)
                this._exModuleOptions = null
            }
            val __contentRoots = _contentRoots!!
            for (item in __contentRoots) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_contentRoots, woBuilder_contentRoots) = __contentRoots.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(CONTENTROOTS_CONNECTION_ID, withBuilder_contentRoots)
            this._contentRoots = if (woBuilder_contentRoots.isNotEmpty()) woBuilder_contentRoots else null
            val __facets = _facets!!
            for (item in __facets) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_facets, woBuilder_facets) = __facets.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(FACETS_CONNECTION_ID, withBuilder_facets)
            this._facets = if (woBuilder_facets.isNotEmpty()) woBuilder_facets else null
            // Process entities from extension fields
            val keysToRemove = ArrayList<ExtRefKey>()
            for ((key, entity) in extReferences) {
                if (!key.isChild()) {
                    continue
                }
                if (entity is List<*>) {
                    for (item in entity) {
                        if (item is ModifiableWorkspaceEntityBase<*>) {
                            builder.addEntity(item)
                        }
                    }
                    entity as List<WorkspaceEntity>
                    val (withBuilder_entity, woBuilder_entity) = entity.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
                    applyRef(key.getConnectionId(), withBuilder_entity)
                    keysToRemove.add(key)
                }
                else {
                    entity as WorkspaceEntity
                    builder.addEntity(entity)
                    applyRef(key.getConnectionId(), entity)
                    keysToRemove.add(key)
                }
            }
            for (key in keysToRemove) {
                extReferences.remove(key)
            }
            
            // Adding parents and references to the parent
            val parentKeysToRemove = ArrayList<ExtRefKey>()
            for ((key, entity) in extReferences) {
                if (key.isChild()) {
                    continue
                }
                if (entity is List<*>) {
                    error("Cannot have parent lists")
                }
                else {
                    entity as WorkspaceEntity
                    builder.addEntity(entity)
                    applyParentRef(key.getConnectionId(), entity)
                    parentKeysToRemove.add(key)
                }
            }
            for (key in parentKeysToRemove) {
                extReferences.remove(key)
            }
            checkInitialization() // TODO uncomment and check failed tests
        }
    
        fun checkInitialization() {
            val _diff = diff
            if (!getEntityData().isNameInitialized()) {
                error("Field ModuleEntity#name should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ModuleEntity#entitySource should be initialized")
            }
            if (!getEntityData().isDependenciesInitialized()) {
                error("Field ModuleEntity#dependencies should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CONTENTROOTS_CONNECTION_ID, this) == null) {
                    error("Field ModuleEntity#contentRoots should be initialized")
                }
            }
            else {
                if (_contentRoots == null) {
                    error("Field ModuleEntity#contentRoots should be initialized")
                }
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(FACETS_CONNECTION_ID, this) == null) {
                    error("Field ModuleEntity#facets should be initialized")
                }
            }
            else {
                if (_facets == null) {
                    error("Field ModuleEntity#facets should be initialized")
                }
            }
        }
    
        
        override var name: String
            get() = getEntityData().name
            set(value) {
                checkModificationAllowed()
                getEntityData().name = value
                changedProperty.add("name")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var type: String?
            get() = getEntityData().type
            set(value) {
                checkModificationAllowed()
                getEntityData().type = value
                changedProperty.add("type")
            }
            
        override var dependencies: List<ModuleDependencyItem>
            get() = getEntityData().dependencies
            set(value) {
                checkModificationAllowed()
                getEntityData().dependencies = value
                
                changedProperty.add("dependencies")
            }
            
            var _contentRoots: List<ContentRootEntity>? = null
            override var contentRoots: List<ContentRootEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyChildren<ContentRootEntity>(CONTENTROOTS_CONNECTION_ID, this)!!.toList() + (_contentRoots ?: emptyList())
                    } else {
                        _contentRoots!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null) {
                        for (item_value in value) {
                            if (item_value is ModifiableWorkspaceEntityBase<*> && (item_value as? ModifiableWorkspaceEntityBase<*>)?.diff == null) {
                                _diff.addEntity(item_value)
                            }
                        }
                        _diff.updateOneToManyChildrenOfParent(CONTENTROOTS_CONNECTION_ID, this, value)
                    }
                    else {
                        for (item_value in value) {
                            if (item_value is ContentRootEntityImpl.Builder) {
                                item_value._module = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _contentRoots = value
                        // Test
                    }
                    changedProperty.add("contentRoots")
                }
        
            var _customImlData: ModuleCustomImlDataEntity? = null
            override var customImlData: ModuleCustomImlDataEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneChild(CUSTOMIMLDATA_CONNECTION_ID, this) ?: _customImlData
                    } else {
                        _customImlData
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ModuleCustomImlDataEntityImpl.Builder) {
                            value._module = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(CUSTOMIMLDATA_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ModuleCustomImlDataEntityImpl.Builder) {
                            value._module = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._customImlData = value
                    }
                    changedProperty.add("customImlData")
                }
        
            var _groupPath: ModuleGroupPathEntity? = null
            override var groupPath: ModuleGroupPathEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneChild(GROUPPATH_CONNECTION_ID, this) ?: _groupPath
                    } else {
                        _groupPath
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ModuleGroupPathEntityImpl.Builder) {
                            value._module = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(GROUPPATH_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ModuleGroupPathEntityImpl.Builder) {
                            value._module = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._groupPath = value
                    }
                    changedProperty.add("groupPath")
                }
        
            var _javaSettings: JavaModuleSettingsEntity? = null
            override var javaSettings: JavaModuleSettingsEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneChild(JAVASETTINGS_CONNECTION_ID, this) ?: _javaSettings
                    } else {
                        _javaSettings
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is JavaModuleSettingsEntityImpl.Builder) {
                            value._module = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(JAVASETTINGS_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is JavaModuleSettingsEntityImpl.Builder) {
                            value._module = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._javaSettings = value
                    }
                    changedProperty.add("javaSettings")
                }
        
            var _exModuleOptions: ExternalSystemModuleOptionsEntity? = null
            override var exModuleOptions: ExternalSystemModuleOptionsEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneChild(EXMODULEOPTIONS_CONNECTION_ID, this) ?: _exModuleOptions
                    } else {
                        _exModuleOptions
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ExternalSystemModuleOptionsEntityImpl.Builder) {
                            value._module = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(EXMODULEOPTIONS_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ExternalSystemModuleOptionsEntityImpl.Builder) {
                            value._module = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._exModuleOptions = value
                    }
                    changedProperty.add("exModuleOptions")
                }
        
            var _facets: List<FacetEntity>? = null
            override var facets: List<FacetEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyChildren<FacetEntity>(FACETS_CONNECTION_ID, this)!!.toList() + (_facets ?: emptyList())
                    } else {
                        _facets!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null) {
                        for (item_value in value) {
                            if (item_value is ModifiableWorkspaceEntityBase<*> && (item_value as? ModifiableWorkspaceEntityBase<*>)?.diff == null) {
                                _diff.addEntity(item_value)
                            }
                        }
                        _diff.updateOneToManyChildrenOfParent(FACETS_CONNECTION_ID, this, value)
                    }
                    else {
                        for (item_value in value) {
                            if (item_value is FacetEntityImpl.Builder) {
                                item_value._module = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _facets = value
                        // Test
                    }
                    changedProperty.add("facets")
                }
        
        override fun getEntityData(): ModuleEntityData = result ?: super.getEntityData() as ModuleEntityData
        override fun getEntityClass(): Class<ModuleEntity> = ModuleEntity::class.java
    }
}
    
class ModuleEntityData : WorkspaceEntityData.WithCalculablePersistentId<ModuleEntity>(), SoftLinkable {
    lateinit var name: String
    var type: String? = null
    lateinit var dependencies: List<ModuleDependencyItem>

    fun isNameInitialized(): Boolean = ::name.isInitialized
    fun isDependenciesInitialized(): Boolean = ::dependencies.isInitialized

    override fun getLinks(): Set<PersistentEntityId<*>> {
        val result = HashSet<PersistentEntityId<*>>()
        for (item in dependencies) {
            val _item = item
            when (_item) {
                is ModuleDependencyItem.Exportable ->  {
                    val __item = _item
                    when (__item) {
                        is ModuleDependencyItem.Exportable.ModuleDependency ->  {
                            result.add(__item.module)
                        }
                        is ModuleDependencyItem.Exportable.LibraryDependency ->  {
                            result.add(__item.library)
                            val ___item_library_tableId = __item.library.tableId
                            when (___item_library_tableId) {
                                is LibraryTableId.ModuleLibraryTableId ->  {
                                    result.add(___item_library_tableId.moduleId)
                                }
                                is LibraryTableId.ProjectLibraryTableId ->  {
                                }
                                is LibraryTableId.GlobalLibraryTableId ->  {
                                }
                            }
                        }
                    }
                }
                is ModuleDependencyItem.SdkDependency ->  {
                }
                is ModuleDependencyItem.InheritedSdkDependency ->  {
                }
                is ModuleDependencyItem.ModuleSourceDependency ->  {
                }
            }
        }
        return result
    }

    override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
        for (item in dependencies) {
            val _item = item
            when (_item) {
                is ModuleDependencyItem.Exportable ->  {
                    val __item = _item
                    when (__item) {
                        is ModuleDependencyItem.Exportable.ModuleDependency ->  {
                            index.index(this, __item.module)
                        }
                        is ModuleDependencyItem.Exportable.LibraryDependency ->  {
                            index.index(this, __item.library)
                            val ___item_library_tableId = __item.library.tableId
                            when (___item_library_tableId) {
                                is LibraryTableId.ModuleLibraryTableId ->  {
                                    index.index(this, ___item_library_tableId.moduleId)
                                }
                                is LibraryTableId.ProjectLibraryTableId ->  {
                                }
                                is LibraryTableId.GlobalLibraryTableId ->  {
                                }
                            }
                        }
                    }
                }
                is ModuleDependencyItem.SdkDependency ->  {
                }
                is ModuleDependencyItem.InheritedSdkDependency ->  {
                }
                is ModuleDependencyItem.ModuleSourceDependency ->  {
                }
            }
        }
    }

    override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
        // TODO verify logic
        val mutablePreviousSet = HashSet(prev)
        for (item in dependencies) {
            val _item = item
            when (_item) {
                is ModuleDependencyItem.Exportable ->  {
                    val __item = _item
                    when (__item) {
                        is ModuleDependencyItem.Exportable.ModuleDependency ->  {
                            val removedItem___item_module = mutablePreviousSet.remove(__item.module)
                            if (!removedItem___item_module) {
                                index.index(this, __item.module)
                            }
                        }
                        is ModuleDependencyItem.Exportable.LibraryDependency ->  {
                            val removedItem___item_library = mutablePreviousSet.remove(__item.library)
                            if (!removedItem___item_library) {
                                index.index(this, __item.library)
                            }
                            val ___item_library_tableId = __item.library.tableId
                            when (___item_library_tableId) {
                                is LibraryTableId.ModuleLibraryTableId ->  {
                                    val removedItem____item_library_tableId_moduleId = mutablePreviousSet.remove(___item_library_tableId.moduleId)
                                    if (!removedItem____item_library_tableId_moduleId) {
                                        index.index(this, ___item_library_tableId.moduleId)
                                    }
                                }
                                is LibraryTableId.ProjectLibraryTableId ->  {
                                }
                                is LibraryTableId.GlobalLibraryTableId ->  {
                                }
                            }
                        }
                    }
                }
                is ModuleDependencyItem.SdkDependency ->  {
                }
                is ModuleDependencyItem.InheritedSdkDependency ->  {
                }
                is ModuleDependencyItem.ModuleSourceDependency ->  {
                }
            }
        }
        for (removed in mutablePreviousSet) {
            index.remove(this, removed)
        }
    }

    override fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean {
        var changed = false
        val dependencies_data = dependencies.map {
            val _it = it
            val res_it =             when (_it) {
                is ModuleDependencyItem.Exportable ->  {
                    val __it = _it
                    val res__it =                     when (__it) {
                        is ModuleDependencyItem.Exportable.ModuleDependency ->  {
                            val __it_module_data =                             if (__it.module == oldLink) {
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
                        is ModuleDependencyItem.Exportable.LibraryDependency ->  {
                            val __it_library_data =                             if (__it.library == oldLink) {
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
                    }
                    res__it
                }
                is ModuleDependencyItem.SdkDependency ->  {
                    _it
                }
                is ModuleDependencyItem.InheritedSdkDependency ->  {
                    _it
                }
                is ModuleDependencyItem.ModuleSourceDependency ->  {
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
            dependencies = dependencies_data
        }
        return changed
    }

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ModuleEntity> {
        val modifiable = ModuleEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ModuleEntity {
        val entity = ModuleEntityImpl()
        entity._name = name
        entity._type = type
        entity._dependencies = dependencies
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun persistentId(): PersistentEntityId<*> {
        return ModuleId(name)
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ModuleEntity::class.java
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ModuleEntityData
        
        if (this.name != other.name) return false
        if (this.entitySource != other.entitySource) return false
        if (this.type != other.type) return false
        if (this.dependencies != other.dependencies) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
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
}