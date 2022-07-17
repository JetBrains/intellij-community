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
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyParentOfChild
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class MiddleEntityImpl: MiddleEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositeBaseEntity::class.java, BaseEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, true)
        
        val connections = listOf<ConnectionId>(
            PARENTENTITY_CONNECTION_ID,
        )

    }
        
    override val parentEntity: CompositeBaseEntity?
        get() = snapshot.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this)           
        
    @JvmField var _property: String? = null
    override val property: String
        get() = _property!!
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: MiddleEntityData?): ModifiableWorkspaceEntityBase<MiddleEntity>(), MiddleEntity.Builder {
        constructor(): this(MiddleEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity MiddleEntity is already created in a different builder")
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
            if (!getEntityData().isPropertyInitialized()) {
                error("Field MiddleEntity#property should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field MiddleEntity#entitySource should be initialized")
            }
        }
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }
    
        
        override var parentEntity: CompositeBaseEntity?
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? CompositeBaseEntity
                } else {
                    this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? CompositeBaseEntity
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
        
        override var property: String
            get() = getEntityData().property
            set(value) {
                checkModificationAllowed()
                getEntityData().property = value
                changedProperty.add("property")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
        
        override fun getEntityData(): MiddleEntityData = result ?: super.getEntityData() as MiddleEntityData
        override fun getEntityClass(): Class<MiddleEntity> = MiddleEntity::class.java
    }
}
    
class MiddleEntityData : WorkspaceEntityData<MiddleEntity>() {
    lateinit var property: String

    fun isPropertyInitialized(): Boolean = ::property.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<MiddleEntity> {
        val modifiable = MiddleEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): MiddleEntity {
        val entity = MiddleEntityImpl()
        entity._property = property
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return MiddleEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as MiddleEntityData
        
        if (this.property != other.property) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as MiddleEntityData
        
        if (this.property != other.property) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + property.hashCode()
        return result
    }
}