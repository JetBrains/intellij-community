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
open class ExternalSystemModuleOptionsEntityImpl: ExternalSystemModuleOptionsEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, ExternalSystemModuleOptionsEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        
        val connections = listOf<ConnectionId>(
            MODULE_CONNECTION_ID,
        )

    }
        
    override val module: ModuleEntity
        get() = snapshot.extractOneToOneParent(MODULE_CONNECTION_ID, this)!!           
        
    @JvmField var _externalSystem: String? = null
    override val externalSystem: String?
        get() = _externalSystem
                        
    @JvmField var _externalSystemModuleVersion: String? = null
    override val externalSystemModuleVersion: String?
        get() = _externalSystemModuleVersion
                        
    @JvmField var _linkedProjectPath: String? = null
    override val linkedProjectPath: String?
        get() = _linkedProjectPath
                        
    @JvmField var _linkedProjectId: String? = null
    override val linkedProjectId: String?
        get() = _linkedProjectId
                        
    @JvmField var _rootProjectPath: String? = null
    override val rootProjectPath: String?
        get() = _rootProjectPath
                        
    @JvmField var _externalSystemModuleGroup: String? = null
    override val externalSystemModuleGroup: String?
        get() = _externalSystemModuleGroup
                        
    @JvmField var _externalSystemModuleType: String? = null
    override val externalSystemModuleType: String?
        get() = _externalSystemModuleType
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: ExternalSystemModuleOptionsEntityData?): ModifiableWorkspaceEntityBase<ExternalSystemModuleOptionsEntity>(), ExternalSystemModuleOptionsEntity.Builder {
        constructor(): this(ExternalSystemModuleOptionsEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
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
            
            // Process linked entities that are connected without a builder
            processLinkedEntities(builder)
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
                if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
                    error("Field ExternalSystemModuleOptionsEntity#module should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ExternalSystemModuleOptionsEntity#entitySource should be initialized")
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
}
    
class ExternalSystemModuleOptionsEntityData : WorkspaceEntityData<ExternalSystemModuleOptionsEntity>() {
    var externalSystem: String? = null
    var externalSystemModuleVersion: String? = null
    var linkedProjectPath: String? = null
    var linkedProjectId: String? = null
    var rootProjectPath: String? = null
    var externalSystemModuleGroup: String? = null
    var externalSystemModuleType: String? = null


    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ExternalSystemModuleOptionsEntity> {
        val modifiable = ExternalSystemModuleOptionsEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ExternalSystemModuleOptionsEntity {
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

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ExternalSystemModuleOptionsEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
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