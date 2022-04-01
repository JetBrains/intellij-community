package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class ParentEntityImpl: ParentEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentEntity::class.java, ChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        internal val CHILDRENCHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentEntity::class.java, ChildChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        internal val OPTIONALCHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentEntity::class.java, ChildWithOptionalParentEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
    }
    
    override val factory: ObjType<*, *>
        get() = ParentEntity
        
    @JvmField var _parentProperty: String? = null
    override val parentProperty: String
        get() = _parentProperty!!
                        
    override val children: List<ChildEntity>
        get() = snapshot.extractOneToManyChildren<ChildEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()
    
    override val childrenChildren: List<ChildChildEntity>
        get() = snapshot.extractOneToManyChildren<ChildChildEntity>(CHILDRENCHILDREN_CONNECTION_ID, this)!!.toList()
    
    override val optionalChildren: List<ChildWithOptionalParentEntity>
        get() = snapshot.extractOneToManyChildren<ChildWithOptionalParentEntity>(OPTIONALCHILDREN_CONNECTION_ID, this)!!.toList()

    class Builder(val result: ParentEntityData?): ModifiableWorkspaceEntityBase<ParentEntity>(), ParentEntity.Builder {
        constructor(): this(ParentEntityData())
                 
        override val factory: ObjType<ParentEntity, *> get() = TODO()
        override fun build(): ParentEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ParentEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            val __children = _children!!
            for (item in __children) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_children, woBuilder_children) = __children.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(CHILDREN_CONNECTION_ID, withBuilder_children)
            this._children = if (woBuilder_children.isNotEmpty()) woBuilder_children else null
            val __childrenChildren = _childrenChildren!!
            for (item in __childrenChildren) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_childrenChildren, woBuilder_childrenChildren) = __childrenChildren.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(CHILDRENCHILDREN_CONNECTION_ID, withBuilder_childrenChildren)
            this._childrenChildren = if (woBuilder_childrenChildren.isNotEmpty()) woBuilder_childrenChildren else null
            val __optionalChildren = _optionalChildren!!
            for (item in __optionalChildren) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_optionalChildren, woBuilder_optionalChildren) = __optionalChildren.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(OPTIONALCHILDREN_CONNECTION_ID, withBuilder_optionalChildren)
            this._optionalChildren = if (woBuilder_optionalChildren.isNotEmpty()) woBuilder_optionalChildren else null
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
            if (!getEntityData().isParentPropertyInitialized()) {
                error("Field ParentEntity#parentProperty should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ParentEntity#entitySource should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
                    error("Field ParentEntity#children should be initialized")
                }
            }
            else {
                if (_children == null) {
                    error("Field ParentEntity#children should be initialized")
                }
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDRENCHILDREN_CONNECTION_ID, this) == null) {
                    error("Field ParentEntity#childrenChildren should be initialized")
                }
            }
            else {
                if (_childrenChildren == null) {
                    error("Field ParentEntity#childrenChildren should be initialized")
                }
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(OPTIONALCHILDREN_CONNECTION_ID, this) == null) {
                    error("Field ParentEntity#optionalChildren should be initialized")
                }
            }
            else {
                if (_optionalChildren == null) {
                    error("Field ParentEntity#optionalChildren should be initialized")
                }
            }
        }
    
        
        override var parentProperty: String
            get() = getEntityData().parentProperty
            set(value) {
                checkModificationAllowed()
                getEntityData().parentProperty = value
                changedProperty.add("parentProperty")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
            var _children: List<ChildEntity>? = null
            override var children: List<ChildEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyChildren<ChildEntity>(CHILDREN_CONNECTION_ID, this)!!.toList() + (_children ?: emptyList())
                    } else {
                        _children!!
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
                        _diff.updateOneToManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value)
                    }
                    else {
                        for (item_value in value) {
                            if (item_value is ChildEntityImpl.Builder) {
                                item_value._parentEntity = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _children = value
                        // Test
                    }
                    changedProperty.add("children")
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
                                item_value._parent1 = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _childrenChildren = value
                        // Test
                    }
                    changedProperty.add("childrenChildren")
                }
        
            var _optionalChildren: List<ChildWithOptionalParentEntity>? = null
            override var optionalChildren: List<ChildWithOptionalParentEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyChildren<ChildWithOptionalParentEntity>(OPTIONALCHILDREN_CONNECTION_ID, this)!!.toList() + (_optionalChildren ?: emptyList())
                    } else {
                        _optionalChildren!!
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
                        _diff.updateOneToManyChildrenOfParent(OPTIONALCHILDREN_CONNECTION_ID, this, value)
                    }
                    else {
                        for (item_value in value) {
                            if (item_value is ChildWithOptionalParentEntityImpl.Builder) {
                                item_value._optionalParent = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _optionalChildren = value
                        // Test
                    }
                    changedProperty.add("optionalChildren")
                }
        
        override fun getEntityData(): ParentEntityData = result ?: super.getEntityData() as ParentEntityData
        override fun getEntityClass(): Class<ParentEntity> = ParentEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(ParentEntityData())
}
    
class ParentEntityData : WorkspaceEntityData<ParentEntity>() {
    lateinit var parentProperty: String

    fun isParentPropertyInitialized(): Boolean = ::parentProperty.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<ParentEntity> {
        val modifiable = ParentEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): ParentEntity {
        val entity = ParentEntityImpl()
        entity._parentProperty = parentProperty
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ParentEntityData
        
        if (this.parentProperty != other.parentProperty) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ParentEntityData
        
        if (this.parentProperty != other.parentProperty) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + parentProperty.hashCode()
        return result
    }
}