package com.intellij.workspace.model.api

import com.intellij.workspace.model.api.FacetEntity.Companion.underlyingFacet
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.SoftLinkable
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class FacetEntityImpl: FacetEntity, WorkspaceEntityBase() {
    
    companion object {
        private val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, FacetEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        private val UNDERLYINGFACET_CONNECTION_ID: ConnectionId = ConnectionId.create(FacetEntity::class.java, FacetEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, true)
    }
    
    override val factory: ObjType<*, *>
        get() = FacetEntity
        
    @JvmField var _name: String? = null
    override val name: String
        get() = _name!!
                        
    override val module: ModuleEntity
        get() = snapshot.extractOneToManyParent(MODULE_CONNECTION_ID, this)!!           
        
    @JvmField var _facetType: String? = null
    override val facetType: String
        get() = _facetType!!
                        
    @JvmField var _configurationXmlTag: String? = null
    override val configurationXmlTag: String
        get() = _configurationXmlTag!!
                        
    @JvmField var _moduleId: ModuleId? = null
    override val moduleId: ModuleId
        get() = _moduleId!!
                        
    override val underlyingFacet: FacetEntity?
        get() = snapshot.extractOneToOneChild(UNDERLYINGFACET_CONNECTION_ID, this)

    class Builder(val result: FacetEntityData?): ModifiableWorkspaceEntityBase<FacetEntity>(), FacetEntity.Builder {
        constructor(): this(FacetEntityData())
                 
        override val factory: ObjType<FacetEntity, *> get() = TODO()
        override fun build(): FacetEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity FacetEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            val __underlyingFacet = _underlyingFacet
            if (__underlyingFacet != null && __underlyingFacet is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__underlyingFacet)
                applyRef(UNDERLYINGFACET_CONNECTION_ID, __underlyingFacet)
                this._underlyingFacet = null
            }
            
            // Adding parents and references to the parent
            val __module = _module
            if (__module != null && (__module is ModifiableWorkspaceEntityBase<*>) && __module.diff == null) {
                builder.addEntity(__module)
            }
            if (__module != null && (__module is ModifiableWorkspaceEntityBase<*>) && __module.diff != null) {
                val __mutFacets = (__module as ModuleEntityImpl.Builder)._facets?.toMutableList()
                __mutFacets?.remove(this)
                __module._facets = if (__mutFacets.isNullOrEmpty()) null else __mutFacets
            }
            if (__module != null) {
                applyParentRef(MODULE_CONNECTION_ID, __module)
                this._module = null
            }
            checkInitialization() // TODO uncomment and check failed tests
        }
    
        fun checkInitialization() {
            val _diff = diff
            if (!getEntityData().isNameInitialized()) {
                error("Field FacetEntity#name should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field FacetEntity#entitySource should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToManyParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
                    error("Field FacetEntity#module should be initialized")
                }
            }
            else {
                if (_module == null) {
                    error("Field FacetEntity#module should be initialized")
                }
            }
            if (!getEntityData().isFacetTypeInitialized()) {
                error("Field FacetEntity#facetType should be initialized")
            }
            if (!getEntityData().isModuleIdInitialized()) {
                error("Field FacetEntity#moduleId should be initialized")
            }
        }
    
        
        override var name: String
            get() = getEntityData().name
            set(value) {
                getEntityData().name = value
                changedProperty.add("name")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
            var _module: ModuleEntity? = null
            override var module: ModuleEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyParent(MODULE_CONNECTION_ID, this) ?: _module!!
                    } else {
                        _module!!
                    }
                }
                set(value) {
                    val _diff = diff
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToManyParentOfChild(MODULE_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ModuleEntityImpl.Builder) {
                            value._facets = (value._facets ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._module = value
                    }
                    changedProperty.add("module")
                }
        
        override var facetType: String
            get() = getEntityData().facetType
            set(value) {
                getEntityData().facetType = value
                changedProperty.add("facetType")
            }
            
        override var configurationXmlTag: String?
            get() = getEntityData().configurationXmlTag
            set(value) {
                getEntityData().configurationXmlTag = value
                changedProperty.add("configurationXmlTag")
            }
            
        override var moduleId: ModuleId
            get() = getEntityData().moduleId
            set(value) {
                getEntityData().moduleId = value
                changedProperty.add("moduleId")
                
            }
            
            var _underlyingFacet: FacetEntity? = null
            override var underlyingFacet: FacetEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneChild(UNDERLYINGFACET_CONNECTION_ID, this) ?: _underlyingFacet
                    } else {
                        _underlyingFacet
                    }
                }
                set(value) {
                    val _diff = diff
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(UNDERLYINGFACET_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is FacetEntityImpl.Builder) {
                            value._underlyingFacet = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._underlyingFacet = value
                    }
                    changedProperty.add("underlyingFacet")
                }
        
        override fun hasNewValue(field: Field<in FacetEntity, *>): Boolean = TODO("Not yet implemented")                                                                     
        override fun <V> setValue(field: Field<in FacetEntity, V>, value: V) = TODO("Not yet implemented")
        override fun getEntityData(): FacetEntityData = result ?: super.getEntityData() as FacetEntityData
        override fun getEntityClass(): Class<FacetEntity> = FacetEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(FacetEntityData())
}
    
class FacetEntityData : WorkspaceEntityData.WithCalculablePersistentId<FacetEntity>(), SoftLinkable {
    lateinit var name: String
    lateinit var facetType: String
    var configurationXmlTag: String? = null
    lateinit var moduleId: ModuleId

    fun isNameInitialized(): Boolean = ::name.isInitialized
    fun isFacetTypeInitialized(): Boolean = ::facetType.isInitialized
    fun isModuleIdInitialized(): Boolean = ::moduleId.isInitialized

    override fun getLinks(): Set<PersistentEntityId<*>> {
        val result = HashSet<PersistentEntityId<*>>()
        val optionalLink_configurationXmlTag = configurationXmlTag
        if (optionalLink_configurationXmlTag != null) {
        }
        result.add(moduleId)
        val optionalLink_underlyingFacet = underlyingFacet
        if (optionalLink_underlyingFacet != null) {
        }
        return result
    }

    override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
        val optionalLink_configurationXmlTag = configurationXmlTag
        if (optionalLink_configurationXmlTag != null) {
        }
        index.index(this, moduleId)
        val optionalLink_underlyingFacet = underlyingFacet
        if (optionalLink_underlyingFacet != null) {
        }
    }

    override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
        // TODO verify logic
        val mutablePreviousSet = HashSet(prev)
        val optionalLink_configurationXmlTag = configurationXmlTag
        if (optionalLink_configurationXmlTag != null) {
        }
        val removedItem_moduleId = mutablePreviousSet.remove(moduleId)
        if (!removedItem_moduleId) {
            index.index(this, moduleId)
        }
        val optionalLink_underlyingFacet = underlyingFacet
        if (optionalLink_underlyingFacet != null) {
        }
        for (removed in mutablePreviousSet) {
            index.remove(this, removed)
        }
    }

    override fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean {
        var changed = false
        val moduleId_data =         if (moduleId == oldLink) {
            changed = true
            newLink as ModuleId
        }
        else {
            null
        }
        if (moduleId_data != null) {
            moduleId = moduleId_data
        }
        return changed
    }

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<FacetEntity> {
        val modifiable = FacetEntityImpl.Builder(null)
        modifiable.diff = diff
        modifiable.snapshot = diff
        modifiable.id = createEntityId()
        modifiable.entitySource = this.entitySource
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): FacetEntity {
        val entity = FacetEntityImpl()
        entity._name = name
        entity._facetType = facetType
        entity._configurationXmlTag = configurationXmlTag
        entity._moduleId = moduleId
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun persistentId(): PersistentEntityId<*> {
        return FacetId(name, facetType, moduleId)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as FacetEntityData
        
        if (this.name != other.name) return false
        if (this.entitySource != other.entitySource) return false
        if (this.facetType != other.facetType) return false
        if (this.configurationXmlTag != other.configurationXmlTag) return false
        if (this.moduleId != other.moduleId) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as FacetEntityData
        
        if (this.name != other.name) return false
        if (this.facetType != other.facetType) return false
        if (this.configurationXmlTag != other.configurationXmlTag) return false
        if (this.moduleId != other.moduleId) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + facetType.hashCode()
        result = 31 * result + configurationXmlTag.hashCode()
        result = 31 * result + moduleId.hashCode()
        return result
    }
}