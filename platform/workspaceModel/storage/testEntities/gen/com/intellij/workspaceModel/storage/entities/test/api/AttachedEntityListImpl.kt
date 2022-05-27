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
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import com.intellij.workspaceModel.storage.referrersx
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class AttachedEntityListImpl: AttachedEntityList, WorkspaceEntityBase() {
    
    companion object {
        internal val REF_CONNECTION_ID: ConnectionId = ConnectionId.create(MainEntityList::class.java, AttachedEntityList::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
    }
        
    override val ref: MainEntityList?
        get() = snapshot.extractOneToManyParent(REF_CONNECTION_ID, this)           
        
    @JvmField var _data: String? = null
    override val data: String
        get() = _data!!

    class Builder(val result: AttachedEntityListData?): ModifiableWorkspaceEntityBase<AttachedEntityList>(), AttachedEntityList.Builder {
        constructor(): this(AttachedEntityListData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity AttachedEntityList is already created in a different builder")
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
            val __ref = _ref
            if (__ref != null && (__ref is ModifiableWorkspaceEntityBase<*>) && __ref.diff == null) {
                builder.addEntity(__ref)
            }
            if (__ref != null && (__ref is ModifiableWorkspaceEntityBase<*>) && __ref.diff != null) {
                // Set field to null (in referenced entity)
                val __mutChild = ((__ref as ModifiableWorkspaceEntityBase<*>).extReferences[ExtRefKey("AttachedEntityList", "ref", true, REF_CONNECTION_ID)] as? List<Any> ?: emptyList()).toMutableList()
                __mutChild.remove(this)
                __ref.extReferences[ExtRefKey("AttachedEntityList", "ref", true, REF_CONNECTION_ID)] = __mutChild
            }
            if (__ref != null) {
                applyParentRef(REF_CONNECTION_ID, __ref)
                this._ref = null
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
                error("Field AttachedEntityList#entitySource should be initialized")
            }
            if (!getEntityData().isDataInitialized()) {
                error("Field AttachedEntityList#data should be initialized")
            }
        }
    
        
            var _ref: MainEntityList? = null
            override var ref: MainEntityList?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyParent(REF_CONNECTION_ID, this) ?: _ref
                    } else {
                        _ref
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        // Back reference for the list of ext field
                        if (value is ModifiableWorkspaceEntityBase<*>) {
                            value.extReferences[ExtRefKey("AttachedEntityList", "ref", true, REF_CONNECTION_ID)] = (value.extReferences[ExtRefKey("AttachedEntityList", "ref", true, REF_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToManyParentOfChild(REF_CONNECTION_ID, this, value)
                    }
                    else {
                        // Back reference for the list of ext field
                        if (value is ModifiableWorkspaceEntityBase<*>) {
                            value.extReferences[ExtRefKey("AttachedEntityList", "ref", true, REF_CONNECTION_ID)] = (value.extReferences[ExtRefKey("AttachedEntityList", "ref", true, REF_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._ref = value
                    }
                    changedProperty.add("ref")
                }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var data: String
            get() = getEntityData().data
            set(value) {
                checkModificationAllowed()
                getEntityData().data = value
                changedProperty.add("data")
            }
        
        override fun getEntityData(): AttachedEntityListData = result ?: super.getEntityData() as AttachedEntityListData
        override fun getEntityClass(): Class<AttachedEntityList> = AttachedEntityList::class.java
    }
}
    
class AttachedEntityListData : WorkspaceEntityData<AttachedEntityList>() {
    lateinit var data: String

    fun isDataInitialized(): Boolean = ::data.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<AttachedEntityList> {
        val modifiable = AttachedEntityListImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): AttachedEntityList {
        val entity = AttachedEntityListImpl()
        entity._data = data
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return AttachedEntityList::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as AttachedEntityListData
        
        if (this.entitySource != other.entitySource) return false
        if (this.data != other.data) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as AttachedEntityListData
        
        if (this.data != other.data) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + data.hashCode()
        return result
    }
}