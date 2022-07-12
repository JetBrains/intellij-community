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
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ArtifactExternalSystemIdEntityImpl: ArtifactExternalSystemIdEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val ARTIFACTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java, ArtifactExternalSystemIdEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        
        val connections = listOf<ConnectionId>(
            ARTIFACTENTITY_CONNECTION_ID,
        )

    }
        
    @JvmField var _externalSystemId: String? = null
    override val externalSystemId: String
        get() = _externalSystemId!!
                        
    override val artifactEntity: ArtifactEntity
        get() = snapshot.extractOneToOneParent(ARTIFACTENTITY_CONNECTION_ID, this)!!
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

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
            
            // Process linked entities that are connected without a builder
            processLinkedEntities(builder)
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
                if (this.entityLinks[EntityLink(false, ARTIFACTENTITY_CONNECTION_ID)] == null) {
                    error("Field ArtifactExternalSystemIdEntity#artifactEntity should be initialized")
                }
            }
        }
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
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
            
        override var artifactEntity: ArtifactEntity
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToOneParent(ARTIFACTENTITY_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false, ARTIFACTENTITY_CONNECTION_ID)]!! as ArtifactEntity
                } else {
                    this.entityLinks[EntityLink(false, ARTIFACTENTITY_CONNECTION_ID)]!! as ArtifactEntity
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(true, ARTIFACTENTITY_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToOneParentOfChild(ARTIFACTENTITY_CONNECTION_ID, this, value)
                }
                else {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(true, ARTIFACTENTITY_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this.entityLinks[EntityLink(false, ARTIFACTENTITY_CONNECTION_ID)] = value
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
        modifiable.changedProperty.clear()
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

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
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