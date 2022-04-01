package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class ChildChildEntityImpl: ChildChildEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val PARENT1_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentEntity::class.java, ChildChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        internal val PARENT2_CONNECTION_ID: ConnectionId = ConnectionId.create(ChildEntity::class.java, ChildChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    }
    
    override val factory: ObjType<*, *>
        get() = ChildChildEntity
        
    override val parent1: ParentEntity
        get() = snapshot.extractOneToManyParent(PARENT1_CONNECTION_ID, this)!!           
        
    override val parent2: ChildEntity
        get() = snapshot.extractOneToManyParent(PARENT2_CONNECTION_ID, this)!!

    class Builder(val result: ChildChildEntityData?): ModifiableWorkspaceEntityBase<ChildChildEntity>(), ChildChildEntity.Builder {
        constructor(): this(ChildChildEntityData())
                 
        override val factory: ObjType<ChildChildEntity, *> get() = TODO()
        override fun build(): ChildChildEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ChildChildEntity is already created in a different builder")
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
            val __parent1 = _parent1
            if (__parent1 != null && (__parent1 is ModifiableWorkspaceEntityBase<*>) && __parent1.diff == null) {
                builder.addEntity(__parent1)
            }
            if (__parent1 != null && (__parent1 is ModifiableWorkspaceEntityBase<*>) && __parent1.diff != null) {
                // Set field to null (in referenced entity)
                val __mutChildrenChildren = (__parent1 as ParentEntityImpl.Builder)._childrenChildren?.toMutableList()
                __mutChildrenChildren?.remove(this)
                __parent1._childrenChildren = if (__mutChildrenChildren.isNullOrEmpty()) null else __mutChildrenChildren
            }
            if (__parent1 != null) {
                applyParentRef(PARENT1_CONNECTION_ID, __parent1)
                this._parent1 = null
            }
            val __parent2 = _parent2
            if (__parent2 != null && (__parent2 is ModifiableWorkspaceEntityBase<*>) && __parent2.diff == null) {
                builder.addEntity(__parent2)
            }
            if (__parent2 != null && (__parent2 is ModifiableWorkspaceEntityBase<*>) && __parent2.diff != null) {
                // Set field to null (in referenced entity)
                val __mutChildrenChildren = (__parent2 as ChildEntityImpl.Builder)._childrenChildren?.toMutableList()
                __mutChildrenChildren?.remove(this)
                __parent2._childrenChildren = if (__mutChildrenChildren.isNullOrEmpty()) null else __mutChildrenChildren
            }
            if (__parent2 != null) {
                applyParentRef(PARENT2_CONNECTION_ID, __parent2)
                this._parent2 = null
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
                if (_diff.extractOneToManyParent<WorkspaceEntityBase>(PARENT1_CONNECTION_ID, this) == null) {
                    error("Field ChildChildEntity#parent1 should be initialized")
                }
            }
            else {
                if (_parent1 == null) {
                    error("Field ChildChildEntity#parent1 should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ChildChildEntity#entitySource should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToManyParent<WorkspaceEntityBase>(PARENT2_CONNECTION_ID, this) == null) {
                    error("Field ChildChildEntity#parent2 should be initialized")
                }
            }
            else {
                if (_parent2 == null) {
                    error("Field ChildChildEntity#parent2 should be initialized")
                }
            }
        }
    
        
            var _parent1: ParentEntity? = null
            override var parent1: ParentEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyParent(PARENT1_CONNECTION_ID, this) ?: _parent1!!
                    } else {
                        _parent1!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ParentEntityImpl.Builder) {
                            value._childrenChildren = (value._childrenChildren ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToManyParentOfChild(PARENT1_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ParentEntityImpl.Builder) {
                            value._childrenChildren = (value._childrenChildren ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._parent1 = value
                    }
                    changedProperty.add("parent1")
                }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
            var _parent2: ChildEntity? = null
            override var parent2: ChildEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyParent(PARENT2_CONNECTION_ID, this) ?: _parent2!!
                    } else {
                        _parent2!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ChildEntityImpl.Builder) {
                            value._childrenChildren = (value._childrenChildren ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToManyParentOfChild(PARENT2_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ChildEntityImpl.Builder) {
                            value._childrenChildren = (value._childrenChildren ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._parent2 = value
                    }
                    changedProperty.add("parent2")
                }
        
        override fun getEntityData(): ChildChildEntityData = result ?: super.getEntityData() as ChildChildEntityData
        override fun getEntityClass(): Class<ChildChildEntity> = ChildChildEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(ChildChildEntityData())
}
    
class ChildChildEntityData : WorkspaceEntityData<ChildChildEntity>() {


    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<ChildChildEntity> {
        val modifiable = ChildChildEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): ChildChildEntity {
        val entity = ChildChildEntityImpl()
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildChildEntityData
        
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildChildEntityData
        
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        return result
    }
}