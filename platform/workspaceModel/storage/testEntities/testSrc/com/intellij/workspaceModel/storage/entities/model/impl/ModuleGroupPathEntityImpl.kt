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
import org.jetbrains.deft.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class ModuleGroupPathEntityImpl: ModuleGroupPathEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, ModuleGroupPathEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
        
    override val module: ModuleEntity
        get() = snapshot.extractOneToOneParent(MODULE_CONNECTION_ID, this)!!           
        
    @JvmField var _path: List<String>? = null
    override val path: List<String>
        get() = _path!!

    class Builder(val result: ModuleGroupPathEntityData?): ModifiableWorkspaceEntityBase<ModuleGroupPathEntity>(), ModuleGroupPathEntity.Builder {
        constructor(): this(ModuleGroupPathEntityData())
                 
        override fun build(): ModuleGroupPathEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ModuleGroupPathEntity is already created in a different builder")
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
                (__module as ModuleEntityImpl.Builder)._groupPath = null
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
                    error("Field ModuleGroupPathEntity#module should be initialized")
                }
            }
            else {
                if (_module == null) {
                    error("Field ModuleGroupPathEntity#module should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ModuleGroupPathEntity#entitySource should be initialized")
            }
            if (!getEntityData().isPathInitialized()) {
                error("Field ModuleGroupPathEntity#path should be initialized")
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
                            value._groupPath = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(MODULE_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ModuleEntityImpl.Builder) {
                            value._groupPath = this
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
            
        override var path: List<String>
            get() = getEntityData().path
            set(value) {
                checkModificationAllowed()
                getEntityData().path = value
                
                changedProperty.add("path")
            }
        
        override fun getEntityData(): ModuleGroupPathEntityData = result ?: super.getEntityData() as ModuleGroupPathEntityData
        override fun getEntityClass(): Class<ModuleGroupPathEntity> = ModuleGroupPathEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(ModuleGroupPathEntityData())
}
    
class ModuleGroupPathEntityData : WorkspaceEntityData<ModuleGroupPathEntity>() {
    lateinit var path: List<String>

    fun isPathInitialized(): Boolean = ::path.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<ModuleGroupPathEntity> {
        val modifiable = ModuleGroupPathEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): ModuleGroupPathEntity {
        val entity = ModuleGroupPathEntityImpl()
        entity._path = path
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ModuleGroupPathEntityData
        
        if (this.entitySource != other.entitySource) return false
        if (this.path != other.path) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ModuleGroupPathEntityData
        
        if (this.path != other.path) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + path.hashCode()
        return result
    }
}