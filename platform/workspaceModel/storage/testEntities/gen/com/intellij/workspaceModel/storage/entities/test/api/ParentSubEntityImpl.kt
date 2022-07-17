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
open class ParentSubEntityImpl: ParentSubEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val CHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentSubEntity::class.java, ChildSubEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        
        val connections = listOf<ConnectionId>(
            CHILD_CONNECTION_ID,
        )

    }
        
    @JvmField var _parentData: String? = null
    override val parentData: String
        get() = _parentData!!
                        
    override val child: ChildSubEntity
        get() = snapshot.extractOneToOneChild(CHILD_CONNECTION_ID, this)!!
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: ParentSubEntityData?): ModifiableWorkspaceEntityBase<ParentSubEntity>(), ParentSubEntity.Builder {
        constructor(): this(ParentSubEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ParentSubEntity is already created in a different builder")
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
                error("Field ParentSubEntity#parentData should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ParentSubEntity#entitySource should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToOneChild<WorkspaceEntityBase>(CHILD_CONNECTION_ID, this) == null) {
                    error("Field ParentSubEntity#child should be initialized")
                }
            }
            else {
                if (this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] == null) {
                    error("Field ParentSubEntity#child should be initialized")
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
            
        override var child: ChildSubEntity
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToOneChild(CHILD_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)]!! as ChildSubEntity
                } else {
                    this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)]!! as ChildSubEntity
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
        
        override fun getEntityData(): ParentSubEntityData = result ?: super.getEntityData() as ParentSubEntityData
        override fun getEntityClass(): Class<ParentSubEntity> = ParentSubEntity::class.java
    }
}
    
class ParentSubEntityData : WorkspaceEntityData<ParentSubEntity>() {
    lateinit var parentData: String

    fun isParentDataInitialized(): Boolean = ::parentData.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ParentSubEntity> {
        val modifiable = ParentSubEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ParentSubEntity {
        val entity = ParentSubEntityImpl()
        entity._parentData = parentData
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ParentSubEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ParentSubEntityData
        
        if (this.parentData != other.parentData) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ParentSubEntityData
        
        if (this.parentData != other.parentData) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + parentData.hashCode()
        return result
    }
}