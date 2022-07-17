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
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class MainEntityToParentImpl: MainEntityToParent, WorkspaceEntityBase() {
    
    companion object {
        internal val CHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(MainEntityToParent::class.java, AttachedEntityToParent::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        
        val connections = listOf<ConnectionId>(
            CHILD_CONNECTION_ID,
        )

    }
        
    override val child: AttachedEntityToParent?
        get() = snapshot.extractOneToOneChild(CHILD_CONNECTION_ID, this)           
        
    @JvmField var _x: String? = null
    override val x: String
        get() = _x!!
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: MainEntityToParentData?): ModifiableWorkspaceEntityBase<MainEntityToParent>(), MainEntityToParent.Builder {
        constructor(): this(MainEntityToParentData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity MainEntityToParent is already created in a different builder")
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
                error("Field MainEntityToParent#entitySource should be initialized")
            }
            if (!getEntityData().isXInitialized()) {
                error("Field MainEntityToParent#x should be initialized")
            }
        }
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }
    
        
        override var child: AttachedEntityToParent?
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToOneChild(CHILD_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] as? AttachedEntityToParent
                } else {
                    this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] as? AttachedEntityToParent
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToOneChildOfParent(CHILD_CONNECTION_ID, this, value)
                }
                else {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] = value
                }
                changedProperty.add("child")
            }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var x: String
            get() = getEntityData().x
            set(value) {
                checkModificationAllowed()
                getEntityData().x = value
                changedProperty.add("x")
            }
        
        override fun getEntityData(): MainEntityToParentData = result ?: super.getEntityData() as MainEntityToParentData
        override fun getEntityClass(): Class<MainEntityToParent> = MainEntityToParent::class.java
    }
}
    
class MainEntityToParentData : WorkspaceEntityData<MainEntityToParent>() {
    lateinit var x: String

    fun isXInitialized(): Boolean = ::x.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<MainEntityToParent> {
        val modifiable = MainEntityToParentImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): MainEntityToParent {
        val entity = MainEntityToParentImpl()
        entity._x = x
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return MainEntityToParent::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as MainEntityToParentData
        
        if (this.entitySource != other.entitySource) return false
        if (this.x != other.x) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as MainEntityToParentData
        
        if (this.x != other.x) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + x.hashCode()
        return result
    }
}