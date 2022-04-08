package com.intellij.workspaceModel.storage.entities.model.api

import com.intellij.workspaceModel.storage.EntitySource
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
import org.jetbrains.deft.ObjBuilder

    

open class ExternalSystemModuleOptionsEntityImpl: ExternalSystemModuleOptionsEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, ExternalSystemModuleOptionsEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
        
    override val module: ModuleEntity
        get() = snapshot.extractOneToOneParent(MODULE_CONNECTION_ID, this)!!           
        
    @JvmField var _externalSystem: String? = null
    override val externalSystem: String
        get() = _externalSystem!!
                        
    @JvmField var _externalSystemModuleVersion: String? = null
    override val externalSystemModuleVersion: String
        get() = _externalSystemModuleVersion!!
                        
    @JvmField var _linkedProjectPath: String? = null
    override val linkedProjectPath: String
        get() = _linkedProjectPath!!
                        
    @JvmField var _linkedProjectId: String? = null
    override val linkedProjectId: String
        get() = _linkedProjectId!!
                        
    @JvmField var _rootProjectPath: String? = null
    override val rootProjectPath: String
        get() = _rootProjectPath!!
                        
    @JvmField var _externalSystemModuleGroup: String? = null
    override val externalSystemModuleGroup: String
        get() = _externalSystemModuleGroup!!
                        
    @JvmField var _externalSystemModuleType: String? = null
    override val externalSystemModuleType: String
        get() = _externalSystemModuleType!!

    class Builder(val result: ExternalSystemModuleOptionsEntityData?): ModifiableWorkspaceEntityBase<ExternalSystemModuleOptionsEntity>(), ExternalSystemModuleOptionsEntity.Builder {
        constructor(): this(ExternalSystemModuleOptionsEntityData())
                 
        override fun build(): ExternalSystemModuleOptionsEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ExternalSystemModuleOptionsEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
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
                (__module as ModuleEntityImpl.Builder)._exModuleOptions = null
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
                    error("Field ExternalSystemModuleOptionsEntity#module should be initialized")
                }
            }
            else {
                if (_module == null) {
                    error("Field ExternalSystemModuleOptionsEntity#module should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ExternalSystemModuleOptionsEntity#entitySource should be initialized")
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
                            value._exModuleOptions = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(MODULE_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ModuleEntityImpl.Builder) {
                            value._exModuleOptions = this
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
            
        override var externalSystem: String?
            get() = getEntityData().externalSystem
            set(value) {
                checkModificationAllowed()
                getEntityData().externalSystem = value
                changedProperty.add("externalSystem")
            }
            
        override var externalSystemModuleVersion: String?
            get() = getEntityData().externalSystemModuleVersion
            set(value) {
                checkModificationAllowed()
                getEntityData().externalSystemModuleVersion = value
                changedProperty.add("externalSystemModuleVersion")
            }
            
        override var linkedProjectPath: String?
            get() = getEntityData().linkedProjectPath
            set(value) {
                checkModificationAllowed()
                getEntityData().linkedProjectPath = value
                changedProperty.add("linkedProjectPath")
            }
            
        override var linkedProjectId: String?
            get() = getEntityData().linkedProjectId
            set(value) {
                checkModificationAllowed()
                getEntityData().linkedProjectId = value
                changedProperty.add("linkedProjectId")
            }
            
        override var rootProjectPath: String?
            get() = getEntityData().rootProjectPath
            set(value) {
                checkModificationAllowed()
                getEntityData().rootProjectPath = value
                changedProperty.add("rootProjectPath")
            }
            
        override var externalSystemModuleGroup: String?
            get() = getEntityData().externalSystemModuleGroup
            set(value) {
                checkModificationAllowed()
                getEntityData().externalSystemModuleGroup = value
                changedProperty.add("externalSystemModuleGroup")
            }
            
        override var externalSystemModuleType: String?
            get() = getEntityData().externalSystemModuleType
            set(value) {
                checkModificationAllowed()
                getEntityData().externalSystemModuleType = value
                changedProperty.add("externalSystemModuleType")
            }
        
        override fun getEntityData(): ExternalSystemModuleOptionsEntityData = result ?: super.getEntityData() as ExternalSystemModuleOptionsEntityData
        override fun getEntityClass(): Class<ExternalSystemModuleOptionsEntity> = ExternalSystemModuleOptionsEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(ExternalSystemModuleOptionsEntityData())
}
    
class ExternalSystemModuleOptionsEntityData : WorkspaceEntityData<ExternalSystemModuleOptionsEntity>() {
    var externalSystem: String? = null
    var externalSystemModuleVersion: String? = null
    var linkedProjectPath: String? = null
    var linkedProjectId: String? = null
    var rootProjectPath: String? = null
    var externalSystemModuleGroup: String? = null
    var externalSystemModuleType: String? = null


    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<ExternalSystemModuleOptionsEntity> {
        val modifiable = ExternalSystemModuleOptionsEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): ExternalSystemModuleOptionsEntity {
        val entity = ExternalSystemModuleOptionsEntityImpl()
        entity._externalSystem = externalSystem
        entity._externalSystemModuleVersion = externalSystemModuleVersion
        entity._linkedProjectPath = linkedProjectPath
        entity._linkedProjectId = linkedProjectId
        entity._rootProjectPath = rootProjectPath
        entity._externalSystemModuleGroup = externalSystemModuleGroup
        entity._externalSystemModuleType = externalSystemModuleType
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ExternalSystemModuleOptionsEntityData
        
        if (this.entitySource != other.entitySource) return false
        if (this.externalSystem != other.externalSystem) return false
        if (this.externalSystemModuleVersion != other.externalSystemModuleVersion) return false
        if (this.linkedProjectPath != other.linkedProjectPath) return false
        if (this.linkedProjectId != other.linkedProjectId) return false
        if (this.rootProjectPath != other.rootProjectPath) return false
        if (this.externalSystemModuleGroup != other.externalSystemModuleGroup) return false
        if (this.externalSystemModuleType != other.externalSystemModuleType) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ExternalSystemModuleOptionsEntityData
        
        if (this.externalSystem != other.externalSystem) return false
        if (this.externalSystemModuleVersion != other.externalSystemModuleVersion) return false
        if (this.linkedProjectPath != other.linkedProjectPath) return false
        if (this.linkedProjectId != other.linkedProjectId) return false
        if (this.rootProjectPath != other.rootProjectPath) return false
        if (this.externalSystemModuleGroup != other.externalSystemModuleGroup) return false
        if (this.externalSystemModuleType != other.externalSystemModuleType) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + externalSystem.hashCode()
        result = 31 * result + externalSystemModuleVersion.hashCode()
        result = 31 * result + linkedProjectPath.hashCode()
        result = 31 * result + linkedProjectId.hashCode()
        result = 31 * result + rootProjectPath.hashCode()
        result = 31 * result + externalSystemModuleGroup.hashCode()
        result = 31 * result + externalSystemModuleType.hashCode()
        return result
    }
}