package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder

    

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class EclipseProjectPropertiesEntityImpl: EclipseProjectPropertiesEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, EclipseProjectPropertiesEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
        
    override val module: ModuleEntity
        get() = snapshot.extractOneToOneParent(MODULE_CONNECTION_ID, this)!!           
        
    @JvmField var _variablePaths: Map<String, String>? = null
    override val variablePaths: Map<String, String>
        get() = _variablePaths!!
    @JvmField var _eclipseUrls: List<VirtualFileUrl>? = null
    override val eclipseUrls: List<VirtualFileUrl>
        get() = _eclipseUrls!!   
    
    @JvmField var _unknownCons: List<String>? = null
    override val unknownCons: List<String>
        get() = _unknownCons!!   
    
    @JvmField var _knownCons: List<String>? = null
    override val knownCons: List<String>
        get() = _knownCons!!   
    
    override var forceConfigureJdk: Boolean = false
    override var expectedModuleSourcePlace: Int = 0
    @JvmField var _srcPlace: Map<String, Int>? = null
    override val srcPlace: Map<String, Int>
        get() = _srcPlace!!

    class Builder(val result: EclipseProjectPropertiesEntityData?): ModifiableWorkspaceEntityBase<EclipseProjectPropertiesEntity>(), EclipseProjectPropertiesEntity.Builder {
        constructor(): this(EclipseProjectPropertiesEntityData())
                 
        override fun build(): EclipseProjectPropertiesEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity EclipseProjectPropertiesEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            index(this, "eclipseUrls", this.eclipseUrls.toHashSet())
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
            val __module = _module
            if (__module != null && (__module is ModifiableWorkspaceEntityBase<*>) && __module.diff == null) {
                builder.addEntity(__module)
            }
            if (__module != null && (__module is ModifiableWorkspaceEntityBase<*>) && __module.diff != null) {
                // Set field to null (in referenced entity)
                __module.extReferences.remove(ExtRefKey("EclipseProjectPropertiesEntity", "module", true, MODULE_CONNECTION_ID))
            }
            if (__module != null) {
                applyParentRef(MODULE_CONNECTION_ID, __module)
                this._module = null
            }
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
            if (_diff != null) {
                if (_diff.extractOneToOneParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
                    error("Field EclipseProjectPropertiesEntity#module should be initialized")
                }
            }
            else {
                if (_module == null) {
                    error("Field EclipseProjectPropertiesEntity#module should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field EclipseProjectPropertiesEntity#entitySource should be initialized")
            }
            if (!getEntityData().isVariablePathsInitialized()) {
                error("Field EclipseProjectPropertiesEntity#variablePaths should be initialized")
            }
            if (!getEntityData().isEclipseUrlsInitialized()) {
                error("Field EclipseProjectPropertiesEntity#eclipseUrls should be initialized")
            }
            if (!getEntityData().isUnknownConsInitialized()) {
                error("Field EclipseProjectPropertiesEntity#unknownCons should be initialized")
            }
            if (!getEntityData().isKnownConsInitialized()) {
                error("Field EclipseProjectPropertiesEntity#knownCons should be initialized")
            }
            if (!getEntityData().isSrcPlaceInitialized()) {
                error("Field EclipseProjectPropertiesEntity#srcPlace should be initialized")
            }
        }
    
        
            var _module: ModuleEntity? = null
            override var module: ModuleEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneParent(MODULE_CONNECTION_ID, this) ?: _module!!
                    } else {
                        _module!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ModuleEntityImpl.Builder) {
                            value.extReferences[ExtRefKey("EclipseProjectPropertiesEntity", "module", true, MODULE_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(MODULE_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ModuleEntityImpl.Builder) {
                            value.extReferences[ExtRefKey("EclipseProjectPropertiesEntity", "module", true, MODULE_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._module = value
                    }
                    changedProperty.add("module")
                }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var variablePaths: Map<String, String>
            get() = getEntityData().variablePaths
            set(value) {
                checkModificationAllowed()
                getEntityData().variablePaths = value
                changedProperty.add("variablePaths")
            }
            
        override var eclipseUrls: List<VirtualFileUrl>
            get() = getEntityData().eclipseUrls
            set(value) {
                checkModificationAllowed()
                getEntityData().eclipseUrls = value
                val _diff = diff
                if (_diff != null) index(this, "eclipseUrls", value.toHashSet())
                changedProperty.add("eclipseUrls")
            }
            
        override var unknownCons: List<String>
            get() = getEntityData().unknownCons
            set(value) {
                checkModificationAllowed()
                getEntityData().unknownCons = value
                
                changedProperty.add("unknownCons")
            }
            
        override var knownCons: List<String>
            get() = getEntityData().knownCons
            set(value) {
                checkModificationAllowed()
                getEntityData().knownCons = value
                
                changedProperty.add("knownCons")
            }
            
        override var forceConfigureJdk: Boolean
            get() = getEntityData().forceConfigureJdk
            set(value) {
                checkModificationAllowed()
                getEntityData().forceConfigureJdk = value
                changedProperty.add("forceConfigureJdk")
            }
            
        override var expectedModuleSourcePlace: Int
            get() = getEntityData().expectedModuleSourcePlace
            set(value) {
                checkModificationAllowed()
                getEntityData().expectedModuleSourcePlace = value
                changedProperty.add("expectedModuleSourcePlace")
            }
            
        override var srcPlace: Map<String, Int>
            get() = getEntityData().srcPlace
            set(value) {
                checkModificationAllowed()
                getEntityData().srcPlace = value
                changedProperty.add("srcPlace")
            }
        
        override fun getEntityData(): EclipseProjectPropertiesEntityData = result ?: super.getEntityData() as EclipseProjectPropertiesEntityData
        override fun getEntityClass(): Class<EclipseProjectPropertiesEntity> = EclipseProjectPropertiesEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(EclipseProjectPropertiesEntityData())
}
    
class EclipseProjectPropertiesEntityData : WorkspaceEntityData<EclipseProjectPropertiesEntity>() {
    lateinit var variablePaths: Map<String, String>
    lateinit var eclipseUrls: List<VirtualFileUrl>
    lateinit var unknownCons: List<String>
    lateinit var knownCons: List<String>
    var forceConfigureJdk: Boolean = false
    var expectedModuleSourcePlace: Int = 0
    lateinit var srcPlace: Map<String, Int>

    fun isVariablePathsInitialized(): Boolean = ::variablePaths.isInitialized
    fun isEclipseUrlsInitialized(): Boolean = ::eclipseUrls.isInitialized
    fun isUnknownConsInitialized(): Boolean = ::unknownCons.isInitialized
    fun isKnownConsInitialized(): Boolean = ::knownCons.isInitialized
    
    
    fun isSrcPlaceInitialized(): Boolean = ::srcPlace.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<EclipseProjectPropertiesEntity> {
        val modifiable = EclipseProjectPropertiesEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): EclipseProjectPropertiesEntity {
        val entity = EclipseProjectPropertiesEntityImpl()
        entity._variablePaths = variablePaths
        entity._eclipseUrls = eclipseUrls
        entity._unknownCons = unknownCons
        entity._knownCons = knownCons
        entity.forceConfigureJdk = forceConfigureJdk
        entity.expectedModuleSourcePlace = expectedModuleSourcePlace
        entity._srcPlace = srcPlace
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as EclipseProjectPropertiesEntityData
        
        if (this.entitySource != other.entitySource) return false
        if (this.variablePaths != other.variablePaths) return false
        if (this.eclipseUrls != other.eclipseUrls) return false
        if (this.unknownCons != other.unknownCons) return false
        if (this.knownCons != other.knownCons) return false
        if (this.forceConfigureJdk != other.forceConfigureJdk) return false
        if (this.expectedModuleSourcePlace != other.expectedModuleSourcePlace) return false
        if (this.srcPlace != other.srcPlace) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as EclipseProjectPropertiesEntityData
        
        if (this.variablePaths != other.variablePaths) return false
        if (this.eclipseUrls != other.eclipseUrls) return false
        if (this.unknownCons != other.unknownCons) return false
        if (this.knownCons != other.knownCons) return false
        if (this.forceConfigureJdk != other.forceConfigureJdk) return false
        if (this.expectedModuleSourcePlace != other.expectedModuleSourcePlace) return false
        if (this.srcPlace != other.srcPlace) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + variablePaths.hashCode()
        result = 31 * result + eclipseUrls.hashCode()
        result = 31 * result + unknownCons.hashCode()
        result = 31 * result + knownCons.hashCode()
        result = 31 * result + forceConfigureJdk.hashCode()
        result = 31 * result + expectedModuleSourcePlace.hashCode()
        result = 31 * result + srcPlace.hashCode()
        return result
    }
}