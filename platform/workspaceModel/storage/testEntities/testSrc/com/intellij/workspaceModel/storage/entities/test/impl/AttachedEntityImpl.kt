package com.intellij.workspaceModel.storage.entities.test.api

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
import org.jetbrains.deft.ObjBuilder

    

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class AttachedEntityImpl: AttachedEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val REF_CONNECTION_ID: ConnectionId = ConnectionId.create(MainEntity::class.java, AttachedEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
        
    override val ref: MainEntity
        get() = snapshot.extractOneToOneParent(REF_CONNECTION_ID, this)!!           
        
    @JvmField var _data: String? = null
    override val data: String
        get() = _data!!

    class Builder(val result: AttachedEntityData?): ModifiableWorkspaceEntityBase<AttachedEntity>(), AttachedEntity.Builder {
        constructor(): this(AttachedEntityData())
                 
        override fun build(): AttachedEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity AttachedEntity is already created in a different builder")
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
            val __ref = _ref
            if (__ref != null && (__ref is ModifiableWorkspaceEntityBase<*>) && __ref.diff == null) {
                builder.addEntity(__ref)
            }
            if (__ref != null && (__ref is ModifiableWorkspaceEntityBase<*>) && __ref.diff != null) {
                // Set field to null (in referenced entity)
                __ref.extReferences.remove(ExtRefKey("AttachedEntity", "ref", true, REF_CONNECTION_ID))
            }
            if (__ref != null) {
                applyParentRef(REF_CONNECTION_ID, __ref)
                this._ref = null
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
                if (_diff.extractOneToOneParent<WorkspaceEntityBase>(REF_CONNECTION_ID, this) == null) {
                    error("Field AttachedEntity#ref should be initialized")
                }
            }
            else {
                if (_ref == null) {
                    error("Field AttachedEntity#ref should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field AttachedEntity#entitySource should be initialized")
            }
            if (!getEntityData().isDataInitialized()) {
                error("Field AttachedEntity#data should be initialized")
            }
        }
    
        
            var _ref: MainEntity? = null
            override var ref: MainEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneParent(REF_CONNECTION_ID, this) ?: _ref!!
                    } else {
                        _ref!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is MainEntityImpl.Builder) {
                            value.extReferences[ExtRefKey("AttachedEntity", "ref", true, REF_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(REF_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is MainEntityImpl.Builder) {
                            value.extReferences[ExtRefKey("AttachedEntity", "ref", true, REF_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._ref = value
                    }
                    changedProperty.add("ref")
                }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var data: String
            get() = getEntityData().data
            set(value) {
                checkModificationAllowed()
                getEntityData().data = value
                changedProperty.add("data")
            }
        
        override fun getEntityData(): AttachedEntityData = result ?: super.getEntityData() as AttachedEntityData
        override fun getEntityClass(): Class<AttachedEntity> = AttachedEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(AttachedEntityData())
}
    
class AttachedEntityData : WorkspaceEntityData<AttachedEntity>() {
    lateinit var data: String

    fun isDataInitialized(): Boolean = ::data.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<AttachedEntity> {
        val modifiable = AttachedEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): AttachedEntity {
        val entity = AttachedEntityImpl()
        entity._data = data
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return AttachedEntity::class.java
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as AttachedEntityData
        
        if (this.entitySource != other.entitySource) return false
        if (this.data != other.data) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as AttachedEntityData
        
        if (this.data != other.data) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + data.hashCode()
        return result
    }
}