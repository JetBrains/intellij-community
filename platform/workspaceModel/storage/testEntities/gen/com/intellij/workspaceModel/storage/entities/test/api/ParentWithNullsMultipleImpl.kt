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
open class ParentWithNullsMultipleImpl: ParentWithNullsMultiple, WorkspaceEntityBase() {
    
    companion object {
        internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentWithNullsMultiple::class.java, ChildWithNullsMultiple::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
        
        val connections = listOf<ConnectionId>(
            CHILDREN_CONNECTION_ID,
        )

    }
        
    @JvmField var _parentData: String? = null
    override val parentData: String
        get() = _parentData!!
                        
    override val children: List<ChildWithNullsMultiple>
        get() = snapshot.extractOneToManyChildren<ChildWithNullsMultiple>(CHILDREN_CONNECTION_ID, this)!!.toList()
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: ParentWithNullsMultipleData?): ModifiableWorkspaceEntityBase<ParentWithNullsMultiple>(), ParentWithNullsMultiple.Builder {
        constructor(): this(ParentWithNullsMultipleData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ParentWithNullsMultiple is already created in a different builder")
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
            if (!getEntityData().isParentDataInitialized()) {
                error("Field ParentWithNullsMultiple#parentData should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ParentWithNullsMultiple#entitySource should be initialized")
            }
            // Check initialization for collection with ref type
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
                    error("Field ParentWithNullsMultiple#children should be initialized")
                }
            }
            else {
                if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
                    error("Field ParentWithNullsMultiple#children should be initialized")
                }
            }
        }
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }
    
        
        override var parentData: String
            get() = getEntityData().parentData
            set(value) {
                checkModificationAllowed()
                getEntityData().parentData = value
                changedProperty.add("parentData")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        // List of non-abstract referenced types
        var _children: List<ChildWithNullsMultiple>? = emptyList()
        override var children: List<ChildWithNullsMultiple>
            get() {
                // Getter of the list of non-abstract referenced types
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToManyChildren<ChildWithNullsMultiple>(CHILDREN_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<ChildWithNullsMultiple> ?: emptyList())
                } else {
                    this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<ChildWithNullsMultiple> ?: emptyList()
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
        
        override fun getEntityData(): ParentWithNullsMultipleData = result ?: super.getEntityData() as ParentWithNullsMultipleData
        override fun getEntityClass(): Class<ParentWithNullsMultiple> = ParentWithNullsMultiple::class.java
    }
}
    
class ParentWithNullsMultipleData : WorkspaceEntityData<ParentWithNullsMultiple>() {
    lateinit var parentData: String

    fun isParentDataInitialized(): Boolean = ::parentData.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ParentWithNullsMultiple> {
        val modifiable = ParentWithNullsMultipleImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ParentWithNullsMultiple {
        val entity = ParentWithNullsMultipleImpl()
        entity._parentData = parentData
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ParentWithNullsMultiple::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ParentWithNullsMultipleData
        
        if (this.parentData != other.parentData) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ParentWithNullsMultipleData
        
        if (this.parentData != other.parentData) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + parentData.hashCode()
        return result
    }
}