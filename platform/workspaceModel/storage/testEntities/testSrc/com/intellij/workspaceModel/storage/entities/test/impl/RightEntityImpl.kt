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
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyChildren
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyParent
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyParentOfChild
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import org.jetbrains.deft.ObjBuilder

    

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class RightEntityImpl: RightEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositeBaseEntity::class.java, BaseEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, true)
        internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositeBaseEntity::class.java, BaseEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, true)
    }
        
    override val parentEntity: CompositeBaseEntity?
        get() = snapshot.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this)           
        
    override val children: List<BaseEntity>
        get() = snapshot.extractOneToAbstractManyChildren<BaseEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()

    class Builder(val result: RightEntityData?): ModifiableWorkspaceEntityBase<RightEntity>(), RightEntity.Builder {
        constructor(): this(RightEntityData())
                 
        override fun build(): RightEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity RightEntity is already created in a different builder")
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
            val __parentEntity = _parentEntity
            if (__parentEntity != null && (__parentEntity is ModifiableWorkspaceEntityBase<*>) && __parentEntity.diff == null) {
                builder.addEntity(__parentEntity)
            }
            if (__parentEntity != null && (__parentEntity is ModifiableWorkspaceEntityBase<*>) && __parentEntity.diff != null) {
                // Set field to null (in referenced entity)
                val access = __parentEntity::class.memberProperties.single { it.name == "_children" } as KMutableProperty1<*, *>
                val __mutChildren = (access.getter.call(__parentEntity) as? List<*>)?.toMutableList()
                __mutChildren?.remove(this)
                access.setter.call(__parentEntity, if (__mutChildren.isNullOrEmpty()) null else __mutChildren)
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
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
                    error("Field CompositeBaseEntity#children should be initialized")
                }
            }
            else {
                if (_children == null) {
                    error("Field CompositeBaseEntity#children should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field CompositeBaseEntity#entitySource should be initialized")
            }
        }
    
        
            var _parentEntity: CompositeBaseEntity? = null
            override var parentEntity: CompositeBaseEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this) ?: _parentEntity
                    } else {
                        _parentEntity
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
                        _diff.updateOneToAbstractManyParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value != null) {
                            val access = value::class.memberProperties.single { it.name == "_children" } as KMutableProperty1<*, *>
                            access.setter.call(value, ((access.getter.call(value) as? List<*>) ?: emptyList<Any>()) + this)
                        }
                        
                        this._parentEntity = value
                    }
                    changedProperty.add("parentEntity")
                }
        
            var _children: List<BaseEntity>? = null
            override var children: List<BaseEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToAbstractManyChildren<BaseEntity>(CHILDREN_CONNECTION_ID, this)!!.toList() + (_children ?: emptyList())
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
                                val access = item_value::class.memberProperties.single { it.name == "_parentEntity" } as KMutableProperty1<*, *>
                                // x
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
        
        override fun getEntityData(): RightEntityData = result ?: super.getEntityData() as RightEntityData
        override fun getEntityClass(): Class<RightEntity> = RightEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(RightEntityData())
}
    
class RightEntityData : WorkspaceEntityData<RightEntity>() {


    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<RightEntity> {
        val modifiable = RightEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): RightEntity {
        val entity = RightEntityImpl()
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as RightEntityData
        
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as RightEntityData
        
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        return result
    }
}