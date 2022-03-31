package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class ChildEntityImpl: ChildEntity, WorkspaceEntityBase() {
    
    companion object {
        /* internal */val CHILDRENCHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(ChildEntity::class.java, ChildChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        /* internal */val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentEntity::class.java, ChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    }
    
    override val factory: ObjType<*, *>
        get() = ChildEntity
        
    @JvmField var _childProperty: String? = null
    override val childProperty: String
        get() = _childProperty!!
                        
    @JvmField var _dataClass: DataClass? = null
    override val dataClass: DataClass?
        get() = _dataClass
                        
    override val childrenChildren: List<ChildChildEntity>
        get() = snapshot.extractOneToManyChildren<ChildChildEntity>(CHILDRENCHILDREN_CONNECTION_ID, this)!!.toList()
    
    override val parentEntity: ParentEntity
        get() = snapshot.extractOneToManyParent(PARENTENTITY_CONNECTION_ID, this)!!

    class Builder(val result: ChildEntityData?): ModifiableWorkspaceEntityBase<ChildEntity>(), ChildEntity.Builder {
        constructor(): this(ChildEntityData())
                 
        override val factory: ObjType<ChildEntity, *> get() = TODO()
        override fun build(): ChildEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ChildEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            val __childrenChildren = _childrenChildren!!
            for (item in __childrenChildren) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_childrenChildren, woBuilder_childrenChildren) = __childrenChildren.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(CHILDRENCHILDREN_CONNECTION_ID, withBuilder_childrenChildren)
            this._childrenChildren = if (woBuilder_childrenChildren.isNotEmpty()) woBuilder_childrenChildren else null
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
                val __mutChildren = (__parentEntity as ParentEntityImpl.Builder)._children?.toMutableList()
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
            if (!getEntityData().isChildPropertyInitialized()) {
                error("Field ChildEntity#childProperty should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ChildEntity#entitySource should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDRENCHILDREN_CONNECTION_ID, this) == null) {
                    error("Field ChildEntity#childrenChildren should be initialized")
                }
            }
            else {
                if (_childrenChildren == null) {
                    error("Field ChildEntity#childrenChildren should be initialized")
                }
            }
            if (_diff != null) {
                if (_diff.extractOneToManyParent<WorkspaceEntityBase>(PARENTENTITY_CONNECTION_ID, this) == null) {
                    error("Field ChildEntity#parentEntity should be initialized")
                }
            }
            else {
                if (_parentEntity == null) {
                    error("Field ChildEntity#parentEntity should be initialized")
                }
            }
        }
    
        
        override var childProperty: String
            get() = getEntityData().childProperty
            set(value) {
                checkModificationAllowed()
                getEntityData().childProperty = value
                changedProperty.add("childProperty")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var dataClass: DataClass?
            get() = getEntityData().dataClass
            set(value) {
                checkModificationAllowed()
                getEntityData().dataClass = value
                changedProperty.add("dataClass")
                
            }
            
            var _childrenChildren: List<ChildChildEntity>? = null
            override var childrenChildren: List<ChildChildEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyChildren<ChildChildEntity>(CHILDRENCHILDREN_CONNECTION_ID, this)!!.toList() + (_childrenChildren ?: emptyList())
                    } else {
                        _childrenChildren!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null) {
                        for (item_value in value) {
                            if ((item_value as? ModifiableWorkspaceEntityBase<*>)?.diff == null) {
                                _diff.addEntity(item_value)
                            }
                        }
                        _diff.updateOneToManyChildrenOfParent(CHILDRENCHILDREN_CONNECTION_ID, this, value)
                    }
                    else {
                        for (item_value in value) {
                            if (item_value is ChildChildEntityImpl.Builder) {
                                item_value._parent2 = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _childrenChildren = value
                        // Test
                    }
                    changedProperty.add("childrenChildren")
                }
        
            var _parentEntity: ParentEntity? = null
            override var parentEntity: ParentEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyParent(PARENTENTITY_CONNECTION_ID, this) ?: _parentEntity!!
                    } else {
                        _parentEntity!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ParentEntityImpl.Builder) {
                            value._children = (value._children ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToManyParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ParentEntityImpl.Builder) {
                            value._children = (value._children ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._parentEntity = value
                    }
                    changedProperty.add("parentEntity")
                }
        
        override fun hasNewValue(field: Field<in ChildEntity, *>): Boolean = TODO("Not yet implemented")                                                                     
        override fun <V> setValue(field: Field<in ChildEntity, V>, value: V) = TODO("Not yet implemented")
        override fun getEntityData(): ChildEntityData = result ?: super.getEntityData() as ChildEntityData
        override fun getEntityClass(): Class<ChildEntity> = ChildEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(ChildEntityData())
}
    
class ChildEntityData : WorkspaceEntityData<ChildEntity>() {
    lateinit var childProperty: String
    var dataClass: DataClass? = null

    fun isChildPropertyInitialized(): Boolean = ::childProperty.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<ChildEntity> {
        val modifiable = ChildEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): ChildEntity {
        val entity = ChildEntityImpl()
        entity._childProperty = childProperty
        entity._dataClass = dataClass
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildEntityData
        
        if (this.childProperty != other.childProperty) return false
        if (this.entitySource != other.entitySource) return false
        if (this.dataClass != other.dataClass) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildEntityData
        
        if (this.childProperty != other.childProperty) return false
        if (this.dataClass != other.dataClass) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + childProperty.hashCode()
        result = 31 * result + dataClass.hashCode()
        return result
    }
}