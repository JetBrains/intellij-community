package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
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
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class XChildChildEntityImpl: XChildChildEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val PARENT1_CONNECTION_ID: ConnectionId = ConnectionId.create(XParentEntity::class.java, XChildChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        internal val PARENT2_CONNECTION_ID: ConnectionId = ConnectionId.create(XChildEntity::class.java, XChildChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    }
        
    override val parent1: XParentEntity
        get() = snapshot.extractOneToManyParent(PARENT1_CONNECTION_ID, this)!!           
        
    override val parent2: XChildEntity
        get() = snapshot.extractOneToManyParent(PARENT2_CONNECTION_ID, this)!!

    class Builder(val result: XChildChildEntityData?): ModifiableWorkspaceEntityBase<XChildChildEntity>(), XChildChildEntity.Builder {
        constructor(): this(XChildChildEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity XChildChildEntity is already created in a different builder")
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
                val __mutChildChild = (__parent1 as XParentEntityImpl.Builder)._childChild?.toMutableList()
                __mutChildChild?.remove(this)
                __parent1._childChild = if (__mutChildChild.isNullOrEmpty()) null else __mutChildChild
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
                val __mutChildChild = (__parent2 as XChildEntityImpl.Builder)._childChild?.toMutableList()
                __mutChildChild?.remove(this)
                __parent2._childChild = if (__mutChildChild.isNullOrEmpty()) null else __mutChildChild
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
                    error("Field XChildChildEntity#parent1 should be initialized")
                }
            }
            else {
                if (_parent1 == null) {
                    error("Field XChildChildEntity#parent1 should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field XChildChildEntity#entitySource should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToManyParent<WorkspaceEntityBase>(PARENT2_CONNECTION_ID, this) == null) {
                    error("Field XChildChildEntity#parent2 should be initialized")
                }
            }
            else {
                if (_parent2 == null) {
                    error("Field XChildChildEntity#parent2 should be initialized")
                }
            }
        }
    
        
            var _parent1: XParentEntity? = null
            override var parent1: XParentEntity
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
                        // Back reference for the list of non-ext field
                        if (value is XParentEntityImpl.Builder) {
                            value._childChild = (value._childChild ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToManyParentOfChild(PARENT1_CONNECTION_ID, this, value)
                    }
                    else {
                        // Back reference for the list of non-ext field
                        if (value is XParentEntityImpl.Builder) {
                            value._childChild = (value._childChild ?: emptyList()) + this
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
            
            var _parent2: XChildEntity? = null
            override var parent2: XChildEntity
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
                        // Back reference for the list of non-ext field
                        if (value is XChildEntityImpl.Builder) {
                            value._childChild = (value._childChild ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToManyParentOfChild(PARENT2_CONNECTION_ID, this, value)
                    }
                    else {
                        // Back reference for the list of non-ext field
                        if (value is XChildEntityImpl.Builder) {
                            value._childChild = (value._childChild ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._parent2 = value
                    }
                    changedProperty.add("parent2")
                }
        
        override fun getEntityData(): XChildChildEntityData = result ?: super.getEntityData() as XChildChildEntityData
        override fun getEntityClass(): Class<XChildChildEntity> = XChildChildEntity::class.java
    }
}
    
class XChildChildEntityData : WorkspaceEntityData<XChildChildEntity>() {


    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<XChildChildEntity> {
        val modifiable = XChildChildEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): XChildChildEntity {
        val entity = XChildChildEntityImpl()
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return XChildChildEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as XChildChildEntityData
        
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as XChildChildEntityData
        
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        return result
    }
}