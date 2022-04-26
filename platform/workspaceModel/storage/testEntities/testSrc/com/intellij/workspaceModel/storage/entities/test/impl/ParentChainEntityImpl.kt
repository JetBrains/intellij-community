package com.intellij.workspaceModel.storage.entities.test.api

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
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractOneChild
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractOneChildOfParent
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class ParentChainEntityImpl: ParentChainEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val ROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentChainEntity::class.java, CompositeAbstractEntity::class.java, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, true)
    }
        
    override val root: CompositeAbstractEntity
        get() = snapshot.extractOneToAbstractOneChild(ROOT_CONNECTION_ID, this)!!

    class Builder(val result: ParentChainEntityData?): ModifiableWorkspaceEntityBase<ParentChainEntity>(), ParentChainEntity.Builder {
        constructor(): this(ParentChainEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ParentChainEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            val __root = _root
            if (__root != null && __root is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__root)
                applyRef(ROOT_CONNECTION_ID, __root)
                this._root = null
            }
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
            if (_diff != null) {
                if (_diff.extractOneToAbstractOneChild<WorkspaceEntityBase>(ROOT_CONNECTION_ID, this) == null) {
                    error("Field ParentChainEntity#root should be initialized")
                }
            }
            else {
                if (_root == null) {
                    error("Field ParentChainEntity#root should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ParentChainEntity#entitySource should be initialized")
            }
        }
    
        
            var _root: CompositeAbstractEntity? = null
            override var root: CompositeAbstractEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToAbstractOneChild(ROOT_CONNECTION_ID, this) ?: _root!!
                    } else {
                        _root!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value != null) {
                            val access = value::class.memberProperties.single { it.name == "_parentEntity" } as KMutableProperty1<*, *>
                            // x
                            access.setter.call(value, this)
                        }
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToAbstractOneChildOfParent(ROOT_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value != null) {
                            val access = value::class.memberProperties.single { it.name == "_parentEntity" } as KMutableProperty1<*, *>
                            // x
                            access.setter.call(value, this)
                        }
                        
                        this._root = value
                    }
                    changedProperty.add("root")
                }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
        
        override fun getEntityData(): ParentChainEntityData = result ?: super.getEntityData() as ParentChainEntityData
        override fun getEntityClass(): Class<ParentChainEntity> = ParentChainEntity::class.java
    }
}
    
class ParentChainEntityData : WorkspaceEntityData<ParentChainEntity>() {


    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ParentChainEntity> {
        val modifiable = ParentChainEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ParentChainEntity {
        val entity = ParentChainEntityImpl()
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ParentChainEntity::class.java
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ParentChainEntityData
        
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ParentChainEntityData
        
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        return result
    }
}