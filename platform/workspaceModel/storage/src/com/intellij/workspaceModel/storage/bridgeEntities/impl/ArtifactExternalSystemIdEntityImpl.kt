package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class ArtifactExternalSystemIdEntityImpl: ArtifactExternalSystemIdEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val ARTIFACTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java, ArtifactExternalSystemIdEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
        
    @JvmField var _externalSystemId: String? = null
    override val externalSystemId: String
        get() = _externalSystemId!!
                        
    override val artifactEntity: ArtifactEntity
        get() = snapshot.extractOneToOneParent(ARTIFACTENTITY_CONNECTION_ID, this)!!

    class Builder(val result: ArtifactExternalSystemIdEntityData?): ModifiableWorkspaceEntityBase<ArtifactExternalSystemIdEntity>(), ArtifactExternalSystemIdEntity.Builder {
        constructor(): this(ArtifactExternalSystemIdEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ArtifactExternalSystemIdEntity is already created in a different builder")
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
            val __artifactEntity = _artifactEntity
            if (__artifactEntity != null && (__artifactEntity is ModifiableWorkspaceEntityBase<*>) && __artifactEntity.diff == null) {
                builder.addEntity(__artifactEntity)
            }
            if (__artifactEntity != null && (__artifactEntity is ModifiableWorkspaceEntityBase<*>) && __artifactEntity.diff != null) {
                // Set field to null (in referenced entity)
                __artifactEntity.extReferences.remove(ExtRefKey("ArtifactExternalSystemIdEntity", "artifactEntity", true, ARTIFACTENTITY_CONNECTION_ID))
            }
            if (__artifactEntity != null) {
                applyParentRef(ARTIFACTENTITY_CONNECTION_ID, __artifactEntity)
                this._artifactEntity = null
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
            if (!getEntityData().isExternalSystemIdInitialized()) {
                error("Field ArtifactExternalSystemIdEntity#externalSystemId should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ArtifactExternalSystemIdEntity#entitySource should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToOneParent<WorkspaceEntityBase>(ARTIFACTENTITY_CONNECTION_ID, this) == null) {
                    error("Field ArtifactExternalSystemIdEntity#artifactEntity should be initialized")
                }
            }
            else {
                if (_artifactEntity == null) {
                    error("Field ArtifactExternalSystemIdEntity#artifactEntity should be initialized")
                }
            }
        }
    
        
        override var externalSystemId: String
            get() = getEntityData().externalSystemId
            set(value) {
                checkModificationAllowed()
                getEntityData().externalSystemId = value
                changedProperty.add("externalSystemId")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
            var _artifactEntity: ArtifactEntity? = null
            override var artifactEntity: ArtifactEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneParent(ARTIFACTENTITY_CONNECTION_ID, this) ?: _artifactEntity!!
                    } else {
                        _artifactEntity!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ArtifactEntityImpl.Builder) {
                            value.extReferences[ExtRefKey("ArtifactExternalSystemIdEntity", "artifactEntity", true, ARTIFACTENTITY_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(ARTIFACTENTITY_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ArtifactEntityImpl.Builder) {
                            value.extReferences[ExtRefKey("ArtifactExternalSystemIdEntity", "artifactEntity", true, ARTIFACTENTITY_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._artifactEntity = value
                    }
                    changedProperty.add("artifactEntity")
                }
        
        override fun getEntityData(): ArtifactExternalSystemIdEntityData = result ?: super.getEntityData() as ArtifactExternalSystemIdEntityData
        override fun getEntityClass(): Class<ArtifactExternalSystemIdEntity> = ArtifactExternalSystemIdEntity::class.java
    }
}
    
class ArtifactExternalSystemIdEntityData : WorkspaceEntityData<ArtifactExternalSystemIdEntity>() {
    lateinit var externalSystemId: String

    fun isExternalSystemIdInitialized(): Boolean = ::externalSystemId.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ArtifactExternalSystemIdEntity> {
        val modifiable = ArtifactExternalSystemIdEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ArtifactExternalSystemIdEntity {
        val entity = ArtifactExternalSystemIdEntityImpl()
        entity._externalSystemId = externalSystemId
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ArtifactExternalSystemIdEntity::class.java
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ArtifactExternalSystemIdEntityData
        
        if (this.externalSystemId != other.externalSystemId) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ArtifactExternalSystemIdEntityData
        
        if (this.externalSystemId != other.externalSystemId) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + externalSystemId.hashCode()
        return result
    }
}