package com.intellij.workspace.model.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class ModuleCustomImlDataEntityImpl: ModuleCustomImlDataEntity, WorkspaceEntityBase() {
    
    companion object {
        private val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, ModuleCustomImlDataEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
    
    override val factory: ObjType<*, *>
        get() = ModuleCustomImlDataEntity
        
    override val module: ModuleEntity
        get() = snapshot.extractOneToOneParent(MODULE_CONNECTION_ID, this)!!           
        
    @JvmField var _rootManagerTagCustomData: String? = null
    override val rootManagerTagCustomData: String
        get() = _rootManagerTagCustomData!!
                        
    @JvmField var _customModuleOptions: Map<String, String>? = null
    override val customModuleOptions: Map<String, String>
        get() = _customModuleOptions!!

    class Builder(val result: ModuleCustomImlDataEntityData?): ModifiableWorkspaceEntityBase<ModuleCustomImlDataEntity>(), ModuleCustomImlDataEntity.Builder {
        constructor(): this(ModuleCustomImlDataEntityData())
                 
        override val factory: ObjType<ModuleCustomImlDataEntity, *> get() = TODO()
        override fun build(): ModuleCustomImlDataEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ModuleCustomImlDataEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            
            // Adding parents and references to the parent
            val __module = _module
            if (__module != null && (__module is ModifiableWorkspaceEntityBase<*>) && __module.diff == null) {
                builder.addEntity(__module)
            }
            if (__module != null && (__module is ModifiableWorkspaceEntityBase<*>) && __module.diff != null) {
                (__module as ModuleEntityImpl.Builder)._customImlData = null
            }
            if (__module != null) {
                applyParentRef(MODULE_CONNECTION_ID, __module)
                this._module = null
            }
            checkInitialization() // TODO uncomment and check failed tests
        }
    
        fun checkInitialization() {
            val _diff = diff
            if (_diff != null) {
                if (_diff.extractOneToOneParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
                    error("Field ModuleCustomImlDataEntity#module should be initialized")
                }
            }
            else {
                if (_module == null) {
                    error("Field ModuleCustomImlDataEntity#module should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ModuleCustomImlDataEntity#entitySource should be initialized")
            }
            if (!getEntityData().isCustomModuleOptionsInitialized()) {
                error("Field ModuleCustomImlDataEntity#customModuleOptions should be initialized")
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
                    val _diff = diff
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(MODULE_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ModuleEntityImpl.Builder) {
                            value._customImlData = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._module = value
                    }
                    changedProperty.add("module")
                }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var rootManagerTagCustomData: String?
            get() = getEntityData().rootManagerTagCustomData
            set(value) {
                getEntityData().rootManagerTagCustomData = value
                changedProperty.add("rootManagerTagCustomData")
            }
            
        override var customModuleOptions: Map<String, String>
            get() = getEntityData().customModuleOptions
            set(value) {
                getEntityData().customModuleOptions = value
                changedProperty.add("customModuleOptions")
            }
        
        override fun hasNewValue(field: Field<in ModuleCustomImlDataEntity, *>): Boolean = TODO("Not yet implemented")                                                                     
        override fun <V> setValue(field: Field<in ModuleCustomImlDataEntity, V>, value: V) = TODO("Not yet implemented")
        override fun getEntityData(): ModuleCustomImlDataEntityData = result ?: super.getEntityData() as ModuleCustomImlDataEntityData
        override fun getEntityClass(): Class<ModuleCustomImlDataEntity> = ModuleCustomImlDataEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(ModuleCustomImlDataEntityData())
}
    
class ModuleCustomImlDataEntityData : WorkspaceEntityData<ModuleCustomImlDataEntity>() {
    var rootManagerTagCustomData: String? = null
    lateinit var customModuleOptions: Map<String, String>

    fun isCustomModuleOptionsInitialized(): Boolean = ::customModuleOptions.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<ModuleCustomImlDataEntity> {
        val modifiable = ModuleCustomImlDataEntityImpl.Builder(null)
        modifiable.diff = diff
        modifiable.snapshot = diff
        modifiable.id = createEntityId()
        modifiable.entitySource = this.entitySource
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): ModuleCustomImlDataEntity {
        val entity = ModuleCustomImlDataEntityImpl()
        entity._rootManagerTagCustomData = rootManagerTagCustomData
        entity._customModuleOptions = customModuleOptions
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ModuleCustomImlDataEntityData
        
        if (this.entitySource != other.entitySource) return false
        if (this.rootManagerTagCustomData != other.rootManagerTagCustomData) return false
        if (this.customModuleOptions != other.customModuleOptions) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
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
}