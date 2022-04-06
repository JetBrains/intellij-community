package com.intellij.workspaceModel.storage.entities.api

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
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class OoChildWithNullableParentEntityImpl: OoChildWithNullableParentEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(OoParentEntity::class.java, OoChildWithNullableParentEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, true)
    }
    
    override val factory: ObjType<*, *>
        get() = OoChildWithNullableParentEntity
        
    override val parentEntity: OoParentEntity?
        get() = snapshot.extractOneToOneParent(PARENTENTITY_CONNECTION_ID, this)

    class Builder(val result: OoChildWithNullableParentEntityData?): ModifiableWorkspaceEntityBase<OoChildWithNullableParentEntity>(), OoChildWithNullableParentEntity.Builder {
        constructor(): this(OoChildWithNullableParentEntityData())
                 
        override val factory: ObjType<OoChildWithNullableParentEntity, *> get() = TODO()
        override fun build(): OoChildWithNullableParentEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity OoChildWithNullableParentEntity is already created in a different builder")
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
                (__parentEntity as OoParentEntityImpl.Builder)._anotherChild = null
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
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field OoChildWithNullableParentEntity#entitySource should be initialized")
            }
        }
    
        
            var _parentEntity: OoParentEntity? = null
            override var parentEntity: OoParentEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneParent(PARENTENTITY_CONNECTION_ID, this) ?: _parentEntity
                    } else {
                        _parentEntity
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is OoParentEntityImpl.Builder) {
                            value._anotherChild = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is OoParentEntityImpl.Builder) {
                            value._anotherChild = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._parentEntity = value
                    }
                    changedProperty.add("parentEntity")
                }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
        
        override fun getEntityData(): OoChildWithNullableParentEntityData = result ?: super.getEntityData() as OoChildWithNullableParentEntityData
        override fun getEntityClass(): Class<OoChildWithNullableParentEntity> = OoChildWithNullableParentEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(OoChildWithNullableParentEntityData())
}
    
class OoChildWithNullableParentEntityData : WorkspaceEntityData<OoChildWithNullableParentEntity>() {


    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<OoChildWithNullableParentEntity> {
        val modifiable = OoChildWithNullableParentEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): OoChildWithNullableParentEntity {
        val entity = OoChildWithNullableParentEntityImpl()
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as OoChildWithNullableParentEntityData
        
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as OoChildWithNullableParentEntityData
        
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        return result
    }
}