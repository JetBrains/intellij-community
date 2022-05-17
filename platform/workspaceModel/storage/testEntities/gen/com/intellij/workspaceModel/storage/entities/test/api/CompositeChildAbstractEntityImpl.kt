package com.intellij.workspaceModel.storage.entities.test.api

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
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyChildren
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyParent
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractOneParent
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyParentOfChild
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractOneParentOfChild
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class CompositeChildAbstractEntityImpl: CompositeChildAbstractEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val PARENTINLIST_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositeAbstractEntity::class.java, SimpleAbstractEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
        internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositeAbstractEntity::class.java, SimpleAbstractEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
        internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentChainEntity::class.java, CompositeAbstractEntity::class.java, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, true)
    }
        
    override val parentInList: CompositeAbstractEntity
        get() = snapshot.extractOneToAbstractManyParent(PARENTINLIST_CONNECTION_ID, this)!!           
        
    override val children: List<SimpleAbstractEntity>
        get() = snapshot.extractOneToAbstractManyChildren<SimpleAbstractEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()
    
    override val parentEntity: ParentChainEntity?
        get() = snapshot.extractOneToAbstractOneParent(PARENTENTITY_CONNECTION_ID, this)

    class Builder(val result: CompositeChildAbstractEntityData?): ModifiableWorkspaceEntityBase<CompositeChildAbstractEntity>(), CompositeChildAbstractEntity.Builder {
        constructor(): this(CompositeChildAbstractEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity CompositeChildAbstractEntity is already created in a different builder")
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
            val __parentInList = _parentInList
            if (__parentInList != null && (__parentInList is ModifiableWorkspaceEntityBase<*>) && __parentInList.diff == null) {
                builder.addEntity(__parentInList)
            }
            if (__parentInList != null && (__parentInList is ModifiableWorkspaceEntityBase<*>) && __parentInList.diff != null) {
                // Set field to null (in referenced entity)
                val access = __parentInList::class.memberProperties.single { it.name == "_children" } as KMutableProperty1<*, *>
                val __mutChildren = (access.getter.call(__parentInList) as? List<*>)?.toMutableList()
                __mutChildren?.remove(this)
                access.setter.call(__parentInList, if (__mutChildren.isNullOrEmpty()) null else __mutChildren)
            }
            if (__parentInList != null) {
                applyParentRef(PARENTINLIST_CONNECTION_ID, __parentInList)
                this._parentInList = null
            }
            val __parentEntity = _parentEntity
            if (__parentEntity != null && (__parentEntity is ModifiableWorkspaceEntityBase<*>) && __parentEntity.diff == null) {
                builder.addEntity(__parentEntity)
            }
            if (__parentEntity != null && (__parentEntity is ModifiableWorkspaceEntityBase<*>) && __parentEntity.diff != null) {
                // Set field to null (in referenced entity)
                (__parentEntity as ParentChainEntityImpl.Builder)._root = null
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
            if (_diff != null) {
                if (_diff.extractOneToAbstractManyParent<WorkspaceEntityBase>(PARENTINLIST_CONNECTION_ID, this) == null) {
                    error("Field SimpleAbstractEntity#parentInList should be initialized")
                }
            }
            else {
                if (_parentInList == null) {
                    error("Field SimpleAbstractEntity#parentInList should be initialized")
                }
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
                    error("Field CompositeAbstractEntity#children should be initialized")
                }
            }
            else {
                if (_children == null) {
                    error("Field CompositeAbstractEntity#children should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field CompositeAbstractEntity#entitySource should be initialized")
            }
        }
    
        
            var _parentInList: CompositeAbstractEntity? = null
            override var parentInList: CompositeAbstractEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToAbstractManyParent(PARENTINLIST_CONNECTION_ID, this) ?: _parentInList!!
                    } else {
                        _parentInList!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value != null) {
                            val access = value::class.memberProperties.single { it.name == "_children" } as KMutableProperty1<*, *>
                            access.setter.call(value, ((access.getter.call(value) as? List<*>) ?: emptyList<Any>()) + this)
                        }
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToAbstractManyParentOfChild(PARENTINLIST_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value != null) {
                            val access = value::class.memberProperties.single { it.name == "_children" } as KMutableProperty1<*, *>
                            access.setter.call(value, ((access.getter.call(value) as? List<*>) ?: emptyList<Any>()) + this)
                        }
                        
                        this._parentInList = value
                    }
                    changedProperty.add("parentInList")
                }
        
            var _children: List<SimpleAbstractEntity>? = null
            override var children: List<SimpleAbstractEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToAbstractManyChildren<SimpleAbstractEntity>(CHILDREN_CONNECTION_ID, this)!!.toList() + (_children ?: emptyList())
                    } else {
                        _children!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null) {
                        for (item_value in value) {
                            if (item_value is ModifiableWorkspaceEntityBase<*> && (item_value as? ModifiableWorkspaceEntityBase<*>)?.diff == null) {
                                _diff.addEntity(item_value)
                            }
                        }
                        _diff.updateOneToAbstractManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value.asSequence())
                    }
                    else {
                        for (item_value in value) {
                            if (item_value != null) {
                                val access = item_value::class.memberProperties.single { it.name == "_parentInList" } as KMutableProperty1<*, *>
                                access.setter.call(item_value, this)
                            }
                        }
                        
                        _children = value
                    }
                    changedProperty.add("children")
                }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
            var _parentEntity: ParentChainEntity? = null
            override var parentEntity: ParentChainEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToAbstractOneParent(PARENTENTITY_CONNECTION_ID, this) ?: _parentEntity
                    } else {
                        _parentEntity
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ParentChainEntityImpl.Builder) {
                            value._root = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToAbstractOneParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ParentChainEntityImpl.Builder) {
                            value._root = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._parentEntity = value
                    }
                    changedProperty.add("parentEntity")
                }
        
        override fun getEntityData(): CompositeChildAbstractEntityData = result ?: super.getEntityData() as CompositeChildAbstractEntityData
        override fun getEntityClass(): Class<CompositeChildAbstractEntity> = CompositeChildAbstractEntity::class.java
    }
}
    
class CompositeChildAbstractEntityData : WorkspaceEntityData<CompositeChildAbstractEntity>() {


    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<CompositeChildAbstractEntity> {
        val modifiable = CompositeChildAbstractEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): CompositeChildAbstractEntity {
        val entity = CompositeChildAbstractEntityImpl()
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return CompositeChildAbstractEntity::class.java
    }

    fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as CompositeChildAbstractEntityData
        
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as CompositeChildAbstractEntityData
        
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        return result
    }
}