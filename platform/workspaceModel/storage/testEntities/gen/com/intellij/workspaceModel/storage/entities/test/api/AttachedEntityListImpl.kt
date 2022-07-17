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
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class AttachedEntityListImpl: AttachedEntityList, WorkspaceEntityBase() {
    
    companion object {
        internal val REF_CONNECTION_ID: ConnectionId = ConnectionId.create(MainEntityList::class.java, AttachedEntityList::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
        
        val connections = listOf<ConnectionId>(
            REF_CONNECTION_ID,
        )

    }
        
    override val ref: MainEntityList?
        get() = snapshot.extractOneToManyParent(REF_CONNECTION_ID, this)           
        
    @JvmField var _data: String? = null
    override val data: String
        get() = _data!!
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

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
            
            // Process linked entities that are connected without a builder
            processLinkedEntities(builder)
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
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }
    
        
        override var ref: MainEntityList?
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToManyParent(REF_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false, REF_CONNECTION_ID)] as? MainEntityList
                } else {
                    this.entityLinks[EntityLink(false, REF_CONNECTION_ID)] as? MainEntityList
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    // Setting backref of the list
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        val data = (value.entityLinks[EntityLink(true, REF_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
                        value.entityLinks[EntityLink(true, REF_CONNECTION_ID)] = data
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToManyParentOfChild(REF_CONNECTION_ID, this, value)
                }
                else {
                    // Setting backref of the list
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        val data = (value.entityLinks[EntityLink(true, REF_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
                        value.entityLinks[EntityLink(true, REF_CONNECTION_ID)] = data
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this.entityLinks[EntityLink(false, REF_CONNECTION_ID)] = value
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
        modifiable.changedProperty.clear()
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