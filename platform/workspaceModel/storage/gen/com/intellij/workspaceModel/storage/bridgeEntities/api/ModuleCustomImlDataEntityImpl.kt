package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ModuleCustomImlDataEntityImpl: ModuleCustomImlDataEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, ModuleCustomImlDataEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        
        val connections = listOf<ConnectionId>(
            MODULE_CONNECTION_ID,
        )

    }
        
    override val module: ModuleEntity
        get() = snapshot.extractOneToOneParent(MODULE_CONNECTION_ID, this)!!           
        
    @JvmField var _rootManagerTagCustomData: String? = null
    override val rootManagerTagCustomData: String?
        get() = _rootManagerTagCustomData
                        
    @JvmField var _customModuleOptions: Map<String, String>? = null
    override val customModuleOptions: Map<String, String>
        get() = _customModuleOptions!!
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: ModuleCustomImlDataEntityData?): ModifiableWorkspaceEntityBase<ModuleCustomImlDataEntity>(), ModuleCustomImlDataEntity.Builder {
        constructor(): this(ModuleCustomImlDataEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
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
            
            // Process linked entities that are connected without a builder
            processLinkedEntities(builder)
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
                if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
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
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }
    
        
        override var module: ModuleEntity
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToOneParent(MODULE_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity
                } else {
                    this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToOneParentOfChild(MODULE_CONNECTION_ID, this, value)
                }
                else {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
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
            
        override var rootManagerTagCustomData: String?
            get() = getEntityData().rootManagerTagCustomData
            set(value) {
                checkModificationAllowed()
                getEntityData().rootManagerTagCustomData = value
                changedProperty.add("rootManagerTagCustomData")
            }
            
        override var customModuleOptions: Map<String, String>
            get() = getEntityData().customModuleOptions
            set(value) {
                checkModificationAllowed()
                getEntityData().customModuleOptions = value
                changedProperty.add("customModuleOptions")
            }
        
        override fun getEntityData(): ModuleCustomImlDataEntityData = result ?: super.getEntityData() as ModuleCustomImlDataEntityData
        override fun getEntityClass(): Class<ModuleCustomImlDataEntity> = ModuleCustomImlDataEntity::class.java
    }
}
    
class ModuleCustomImlDataEntityData : WorkspaceEntityData<ModuleCustomImlDataEntity>() {
    var rootManagerTagCustomData: String? = null
    lateinit var customModuleOptions: Map<String, String>

    fun isCustomModuleOptionsInitialized(): Boolean = ::customModuleOptions.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ModuleCustomImlDataEntity> {
        val modifiable = ModuleCustomImlDataEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ModuleCustomImlDataEntity {
        val entity = ModuleCustomImlDataEntityImpl()
        entity._rootManagerTagCustomData = rootManagerTagCustomData
        entity._customModuleOptions = customModuleOptions
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ModuleCustomImlDataEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
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