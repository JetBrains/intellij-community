package com.intellij.workspaceModel.storage.bridgeEntities.api

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

    

open class FacetExternalSystemIdEntityImpl: FacetExternalSystemIdEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val FACET_CONNECTION_ID: ConnectionId = ConnectionId.create(FacetEntity::class.java, FacetExternalSystemIdEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
        
    @JvmField var _externalSystemId: String? = null
    override val externalSystemId: String
        get() = _externalSystemId!!
                        
    override val facet: FacetEntity
        get() = snapshot.extractOneToOneParent(FACET_CONNECTION_ID, this)!!

    class Builder(val result: FacetExternalSystemIdEntityData?): ModifiableWorkspaceEntityBase<FacetExternalSystemIdEntity>(), FacetExternalSystemIdEntity.Builder {
        constructor(): this(FacetExternalSystemIdEntityData())
                 
        override fun build(): FacetExternalSystemIdEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity FacetExternalSystemIdEntity is already created in a different builder")
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
            val __facet = _facet
            if (__facet != null && (__facet is ModifiableWorkspaceEntityBase<*>) && __facet.diff == null) {
                builder.addEntity(__facet)
            }
            if (__facet != null && (__facet is ModifiableWorkspaceEntityBase<*>) && __facet.diff != null) {
                // Set field to null (in referenced entity)
                __facet.extReferences.remove(ExtRefKey("FacetExternalSystemIdEntity", "facet", true, FACET_CONNECTION_ID))
            }
            if (__facet != null) {
                applyParentRef(FACET_CONNECTION_ID, __facet)
                this._facet = null
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
                error("Field FacetExternalSystemIdEntity#externalSystemId should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field FacetExternalSystemIdEntity#entitySource should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToOneParent<WorkspaceEntityBase>(FACET_CONNECTION_ID, this) == null) {
                    error("Field FacetExternalSystemIdEntity#facet should be initialized")
                }
            }
            else {
                if (_facet == null) {
                    error("Field FacetExternalSystemIdEntity#facet should be initialized")
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
            
            var _facet: FacetEntity? = null
            override var facet: FacetEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneParent(FACET_CONNECTION_ID, this) ?: _facet!!
                    } else {
                        _facet!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is FacetEntityImpl.Builder) {
                            value.extReferences[ExtRefKey("FacetExternalSystemIdEntity", "facet", true, FACET_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(FACET_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is FacetEntityImpl.Builder) {
                            value.extReferences[ExtRefKey("FacetExternalSystemIdEntity", "facet", true, FACET_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._facet = value
                    }
                    changedProperty.add("facet")
                }
        
        override fun getEntityData(): FacetExternalSystemIdEntityData = result ?: super.getEntityData() as FacetExternalSystemIdEntityData
        override fun getEntityClass(): Class<FacetExternalSystemIdEntity> = FacetExternalSystemIdEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(FacetExternalSystemIdEntityData())
}
    
class FacetExternalSystemIdEntityData : WorkspaceEntityData<FacetExternalSystemIdEntity>() {
    lateinit var externalSystemId: String

    fun isExternalSystemIdInitialized(): Boolean = ::externalSystemId.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<FacetExternalSystemIdEntity> {
        val modifiable = FacetExternalSystemIdEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): FacetExternalSystemIdEntity {
        val entity = FacetExternalSystemIdEntityImpl()
        entity._externalSystemId = externalSystemId
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as FacetExternalSystemIdEntityData
        
        if (this.externalSystemId != other.externalSystemId) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as FacetExternalSystemIdEntityData
        
        if (this.externalSystemId != other.externalSystemId) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + externalSystemId.hashCode()
        return result
    }
}