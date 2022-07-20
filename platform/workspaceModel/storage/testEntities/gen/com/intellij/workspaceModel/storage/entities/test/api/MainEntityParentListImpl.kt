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
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class MainEntityParentListImpl: MainEntityParentList, WorkspaceEntityBase() {
    
    companion object {
        internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(MainEntityParentList::class.java, AttachedEntityParentList::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
        
        val connections = listOf<ConnectionId>(
            CHILDREN_CONNECTION_ID,
        )

    }
        
    @JvmField var _x: String? = null
    override val x: String
        get() = _x!!
                        
    override val children: List<AttachedEntityParentList>
        get() = snapshot.extractOneToManyChildren<AttachedEntityParentList>(CHILDREN_CONNECTION_ID, this)!!.toList()
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: MainEntityParentListData?): ModifiableWorkspaceEntityBase<MainEntityParentList>(), MainEntityParentList.Builder {
        constructor(): this(MainEntityParentListData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity MainEntityParentList is already created in a different builder")
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
            if (!getEntityData().isXInitialized()) {
                error("Field MainEntityParentList#x should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field MainEntityParentList#entitySource should be initialized")
            }
            // Check initialization for collection with ref type
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
                    error("Field MainEntityParentList#children should be initialized")
                }
            }
            else {
                if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
                    error("Field MainEntityParentList#children should be initialized")
                }
            }
        }
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }
    
        
        override var x: String
            get() = getEntityData().x
            set(value) {
                checkModificationAllowed()
                getEntityData().x = value
                changedProperty.add("x")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        // List of non-abstract referenced types
        var _children: List<AttachedEntityParentList>? = emptyList()
        override var children: List<AttachedEntityParentList>
            get() {
                // Getter of the list of non-abstract referenced types
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToManyChildren<AttachedEntityParentList>(CHILDREN_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<AttachedEntityParentList> ?: emptyList())
                } else {
                    this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<AttachedEntityParentList> ?: emptyList()
                }
            }
            set(value) {
                // Setter of the list of non-abstract referenced types
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null) {
                    for (item_value in value) {
                        if (item_value is ModifiableWorkspaceEntityBase<*> && (item_value as? ModifiableWorkspaceEntityBase<*>)?.diff == null) {
                            _diff.addEntity(item_value)
                        }
                    }
                    _diff.updateOneToManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value)
                }
                else {
                    for (item_value in value) {
                        if (item_value is ModifiableWorkspaceEntityBase<*>) {
                            item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                    }
                    
                    this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] = value
                }
                changedProperty.add("children")
            }
        
        override fun getEntityData(): MainEntityParentListData = result ?: super.getEntityData() as MainEntityParentListData
        override fun getEntityClass(): Class<MainEntityParentList> = MainEntityParentList::class.java
    }
}
    
class MainEntityParentListData : WorkspaceEntityData<MainEntityParentList>() {
    lateinit var x: String

    fun isXInitialized(): Boolean = ::x.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<MainEntityParentList> {
        val modifiable = MainEntityParentListImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): MainEntityParentList {
        val entity = MainEntityParentListImpl()
        entity._x = x
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return MainEntityParentList::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as MainEntityParentListData
        
        if (this.x != other.x) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as MainEntityParentListData
        
        if (this.x != other.x) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + x.hashCode()
        return result
    }
}