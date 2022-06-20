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
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyParentOfChild
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ChildSecondEntityImpl: ChildSecondEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentAbEntity::class.java, ChildAbstractBaseEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
        
        val connections = listOf<ConnectionId>(
            PARENTENTITY_CONNECTION_ID,
        )

    }
        
    @JvmField var _commonData: String? = null
    override val commonData: String
        get() = _commonData!!
                        
    override val parentEntity: ParentAbEntity
        get() = snapshot.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this)!!           
        
    @JvmField var _secondData: String? = null
    override val secondData: String
        get() = _secondData!!
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: ChildSecondEntityData?): ModifiableWorkspaceEntityBase<ChildSecondEntity>(), ChildSecondEntity.Builder {
        constructor(): this(ChildSecondEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ChildSecondEntity is already created in a different builder")
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
            if (!getEntityData().isCommonDataInitialized()) {
                error("Field ChildAbstractBaseEntity#commonData should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToAbstractManyParent<WorkspaceEntityBase>(PARENTENTITY_CONNECTION_ID, this) == null) {
                    error("Field ChildAbstractBaseEntity#parentEntity should be initialized")
                }
            }
            else {
                if (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] == null) {
                    error("Field ChildAbstractBaseEntity#parentEntity should be initialized")
                }
            }
            if (!getEntityData().isSecondDataInitialized()) {
                error("Field ChildSecondEntity#secondData should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ChildSecondEntity#entitySource should be initialized")
            }
        }
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }
    
        
        override var commonData: String
            get() = getEntityData().commonData
            set(value) {
                checkModificationAllowed()
                getEntityData().commonData = value
                changedProperty.add("commonData")
            }
            
        override var parentEntity: ParentAbEntity
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)]!! as ParentAbEntity
                } else {
                    this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)]!! as ParentAbEntity
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    // Setting backref of the list
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
                        value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToAbstractManyParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
                }
                else {
                    // Setting backref of the list
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
                        value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] = value
                }
                changedProperty.add("parentEntity")
            }
        
        override var secondData: String
            get() = getEntityData().secondData
            set(value) {
                checkModificationAllowed()
                getEntityData().secondData = value
                changedProperty.add("secondData")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
        
        override fun getEntityData(): ChildSecondEntityData = result ?: super.getEntityData() as ChildSecondEntityData
        override fun getEntityClass(): Class<ChildSecondEntity> = ChildSecondEntity::class.java
    }
}
    
class ChildSecondEntityData : WorkspaceEntityData<ChildSecondEntity>() {
    lateinit var commonData: String
    lateinit var secondData: String

    fun isCommonDataInitialized(): Boolean = ::commonData.isInitialized
    fun isSecondDataInitialized(): Boolean = ::secondData.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ChildSecondEntity> {
        val modifiable = ChildSecondEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ChildSecondEntity {
        val entity = ChildSecondEntityImpl()
        entity._commonData = commonData
        entity._secondData = secondData
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ChildSecondEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildSecondEntityData
        
        if (this.commonData != other.commonData) return false
        if (this.secondData != other.secondData) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildSecondEntityData
        
        if (this.commonData != other.commonData) return false
        if (this.secondData != other.secondData) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + commonData.hashCode()
        result = 31 * result + secondData.hashCode()
        return result
    }
}