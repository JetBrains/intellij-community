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
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import com.intellij.workspaceModel.storage.referrersx
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class ArtifactPropertiesEntityImpl: ArtifactPropertiesEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val ARTIFACT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java, ArtifactPropertiesEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    }
        
    override val artifact: ArtifactEntity
        get() = snapshot.extractOneToManyParent(ARTIFACT_CONNECTION_ID, this)!!           
        
    @JvmField var _providerType: String? = null
    override val providerType: String
        get() = _providerType!!
                        
    @JvmField var _propertiesXmlTag: String? = null
    override val propertiesXmlTag: String?
        get() = _propertiesXmlTag

    class Builder(val result: ArtifactPropertiesEntityData?): ModifiableWorkspaceEntityBase<ArtifactPropertiesEntity>(), ArtifactPropertiesEntity.Builder {
        constructor(): this(ArtifactPropertiesEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ArtifactPropertiesEntity is already created in a different builder")
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
            val __artifact = _artifact
            if (__artifact != null && (__artifact is ModifiableWorkspaceEntityBase<*>) && __artifact.diff == null) {
                builder.addEntity(__artifact)
            }
            if (__artifact != null && (__artifact is ModifiableWorkspaceEntityBase<*>) && __artifact.diff != null) {
                // Set field to null (in referenced entity)
                val __mutCustomProperties = (__artifact as ArtifactEntityImpl.Builder)._customProperties?.toMutableList()
                __mutCustomProperties?.remove(this)
                __artifact._customProperties = if (__mutCustomProperties.isNullOrEmpty()) null else __mutCustomProperties
            }
            if (__artifact != null) {
                applyParentRef(ARTIFACT_CONNECTION_ID, __artifact)
                this._artifact = null
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
                if (_diff.extractOneToManyParent<WorkspaceEntityBase>(ARTIFACT_CONNECTION_ID, this) == null) {
                    error("Field ArtifactPropertiesEntity#artifact should be initialized")
                }
            }
            else {
                if (_artifact == null) {
                    error("Field ArtifactPropertiesEntity#artifact should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ArtifactPropertiesEntity#entitySource should be initialized")
            }
            if (!getEntityData().isProviderTypeInitialized()) {
                error("Field ArtifactPropertiesEntity#providerType should be initialized")
            }
        }
    
        
        var _artifact: ArtifactEntity? = null
        override var artifact: ArtifactEntity
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToManyParent(ARTIFACT_CONNECTION_ID, this) ?: _artifact!!
                } else {
                    _artifact!!
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    // Back reference for the list of non-ext field
                    if (value is ArtifactEntityImpl.Builder) {
                        value._customProperties = (value._customProperties ?: emptyList()) + this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToManyParentOfChild(ARTIFACT_CONNECTION_ID, this, value)
                }
                else {
                    // Back reference for the list of non-ext field
                    if (value is ArtifactEntityImpl.Builder) {
                        value._customProperties = (value._customProperties ?: emptyList()) + this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this._artifact = value
                }
                changedProperty.add("artifact")
            }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var providerType: String
            get() = getEntityData().providerType
            set(value) {
                checkModificationAllowed()
                getEntityData().providerType = value
                changedProperty.add("providerType")
            }
            
        override var propertiesXmlTag: String?
            get() = getEntityData().propertiesXmlTag
            set(value) {
                checkModificationAllowed()
                getEntityData().propertiesXmlTag = value
                changedProperty.add("propertiesXmlTag")
            }
        
        override fun getEntityData(): ArtifactPropertiesEntityData = result ?: super.getEntityData() as ArtifactPropertiesEntityData
        override fun getEntityClass(): Class<ArtifactPropertiesEntity> = ArtifactPropertiesEntity::class.java
    }
}
    
class ArtifactPropertiesEntityData : WorkspaceEntityData<ArtifactPropertiesEntity>() {
    lateinit var providerType: String
    var propertiesXmlTag: String? = null

    fun isProviderTypeInitialized(): Boolean = ::providerType.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ArtifactPropertiesEntity> {
        val modifiable = ArtifactPropertiesEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ArtifactPropertiesEntity {
        val entity = ArtifactPropertiesEntityImpl()
        entity._providerType = providerType
        entity._propertiesXmlTag = propertiesXmlTag
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ArtifactPropertiesEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ArtifactPropertiesEntityData
        
        if (this.entitySource != other.entitySource) return false
        if (this.providerType != other.providerType) return false
        if (this.propertiesXmlTag != other.propertiesXmlTag) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ArtifactPropertiesEntityData
        
        if (this.providerType != other.providerType) return false
        if (this.propertiesXmlTag != other.propertiesXmlTag) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + providerType.hashCode()
        result = 31 * result + propertiesXmlTag.hashCode()
        return result
    }
}