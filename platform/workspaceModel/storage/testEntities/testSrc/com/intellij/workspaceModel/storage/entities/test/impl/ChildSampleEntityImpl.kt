package com.intellij.workspaceModel.storage.entities.test.api

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
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import org.jetbrains.deft.ObjBuilder

    

open class ChildSampleEntityImpl: ChildSampleEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(SampleEntity::class.java, ChildSampleEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
    }
        
    @JvmField var _data: String? = null
    override val data: String
        get() = _data!!
                        
    override val parentEntity: SampleEntity?
        get() = snapshot.extractOneToManyParent(PARENTENTITY_CONNECTION_ID, this)

    class Builder(val result: ChildSampleEntityData?): ModifiableWorkspaceEntityBase<ChildSampleEntity>(), ChildSampleEntity.Builder {
        constructor(): this(ChildSampleEntityData())
                 
        override fun build(): ChildSampleEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ChildSampleEntity is already created in a different builder")
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
            val __parentEntity = _parentEntity
            if (__parentEntity != null && (__parentEntity is ModifiableWorkspaceEntityBase<*>) && __parentEntity.diff == null) {
                builder.addEntity(__parentEntity)
            }
            if (__parentEntity != null && (__parentEntity is ModifiableWorkspaceEntityBase<*>) && __parentEntity.diff != null) {
                // Set field to null (in referenced entity)
                val __mutChildren = (__parentEntity as SampleEntityImpl.Builder)._children?.toMutableList()
                __mutChildren?.remove(this)
                __parentEntity._children = if (__mutChildren.isNullOrEmpty()) null else __mutChildren
            }
            if (__parentEntity != null) {
                applyParentRef(PARENTENTITY_CONNECTION_ID, __parentEntity)
                this._parentEntity = null
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
            if (!getEntityData().isDataInitialized()) {
                error("Field ChildSampleEntity#data should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ChildSampleEntity#entitySource should be initialized")
            }
        }
    
        
        override var data: String
            get() = getEntityData().data
            set(value) {
                checkModificationAllowed()
                getEntityData().data = value
                changedProperty.add("data")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
            var _parentEntity: SampleEntity? = null
            override var parentEntity: SampleEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyParent(PARENTENTITY_CONNECTION_ID, this) ?: _parentEntity
                    } else {
                        _parentEntity
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is SampleEntityImpl.Builder) {
                            value._children = (value._children ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToManyParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is SampleEntityImpl.Builder) {
                            value._children = (value._children ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._parentEntity = value
                    }
                    changedProperty.add("parentEntity")
                }
        
        override fun getEntityData(): ChildSampleEntityData = result ?: super.getEntityData() as ChildSampleEntityData
        override fun getEntityClass(): Class<ChildSampleEntity> = ChildSampleEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(ChildSampleEntityData())
}
    
class ChildSampleEntityData : WorkspaceEntityData<ChildSampleEntity>() {
    lateinit var data: String

    fun isDataInitialized(): Boolean = ::data.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<ChildSampleEntity> {
        val modifiable = ChildSampleEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): ChildSampleEntity {
        val entity = ChildSampleEntityImpl()
        entity._data = data
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildSampleEntityData
        
        if (this.data != other.data) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildSampleEntityData
        
        if (this.data != other.data) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + data.hashCode()
        return result
    }
}