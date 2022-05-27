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
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class ChildSubEntityImpl: ChildSubEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentSubEntity::class.java, ChildSubEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        internal val CHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(ChildSubEntity::class.java, ChildSubSubEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
        
    override val parentEntity: ParentSubEntity
        get() = snapshot.extractOneToOneParent(PARENTENTITY_CONNECTION_ID, this)!!           
        
    override val child: ChildSubSubEntity
        get() = snapshot.extractOneToOneChild(CHILD_CONNECTION_ID, this)!!

    class Builder(val result: ChildSubEntityData?): ModifiableWorkspaceEntityBase<ChildSubEntity>(), ChildSubEntity.Builder {
        constructor(): this(ChildSubEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ChildSubEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            val __child = _child
            if (__child != null && __child is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__child)
                applyRef(CHILD_CONNECTION_ID, __child)
                this._child = null
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
            val __parentEntity = _parentEntity
            if (__parentEntity != null && (__parentEntity is ModifiableWorkspaceEntityBase<*>) && __parentEntity.diff == null) {
                builder.addEntity(__parentEntity)
            }
            if (__parentEntity != null && (__parentEntity is ModifiableWorkspaceEntityBase<*>) && __parentEntity.diff != null) {
                // Set field to null (in referenced entity)
                (__parentEntity as ParentSubEntityImpl.Builder)._child = null
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
                if (_diff.extractOneToOneParent<WorkspaceEntityBase>(PARENTENTITY_CONNECTION_ID, this) == null) {
                    error("Field ChildSubEntity#parentEntity should be initialized")
                }
            }
            else {
                if (_parentEntity == null) {
                    error("Field ChildSubEntity#parentEntity should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ChildSubEntity#entitySource should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToOneChild<WorkspaceEntityBase>(CHILD_CONNECTION_ID, this) == null) {
                    error("Field ChildSubEntity#child should be initialized")
                }
            }
            else {
                if (_child == null) {
                    error("Field ChildSubEntity#child should be initialized")
                }
            }
        }
    
        
            var _parentEntity: ParentSubEntity? = null
            override var parentEntity: ParentSubEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneParent(PARENTENTITY_CONNECTION_ID, this) ?: _parentEntity!!
                    } else {
                        _parentEntity!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        // Back reference for a reference of non-ext field
                        if (value is ParentSubEntityImpl.Builder) {
                            value._child = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
                    }
                    else {
                        // Back reference for a reference of non-ext field
                        if (value is ParentSubEntityImpl.Builder) {
                            value._child = this
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
            
            var _child: ChildSubSubEntity? = null
            override var child: ChildSubSubEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneChild(CHILD_CONNECTION_ID, this) ?: _child!!
                    } else {
                        _child!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        // Back reference for a reference of non-ext field
                        if (value is ChildSubSubEntityImpl.Builder) {
                            value._parentEntity = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(CHILD_CONNECTION_ID, this, value)
                    }
                    else {
                        // Back reference for a reference of non-ext field
                        if (value is ChildSubSubEntityImpl.Builder) {
                            value._parentEntity = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._child = value
                    }
                    changedProperty.add("child")
                }
        
        override fun getEntityData(): ChildSubEntityData = result ?: super.getEntityData() as ChildSubEntityData
        override fun getEntityClass(): Class<ChildSubEntity> = ChildSubEntity::class.java
    }
}
    
class ChildSubEntityData : WorkspaceEntityData<ChildSubEntity>() {


    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ChildSubEntity> {
        val modifiable = ChildSubEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ChildSubEntity {
        val entity = ChildSubEntityImpl()
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ChildSubEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildSubEntityData
        
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildSubEntityData
        
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        return result
    }
}