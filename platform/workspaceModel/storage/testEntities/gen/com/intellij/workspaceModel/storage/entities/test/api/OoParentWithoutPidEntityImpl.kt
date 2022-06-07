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
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class OoParentWithoutPidEntityImpl: OoParentWithoutPidEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val CHILDONE_CONNECTION_ID: ConnectionId = ConnectionId.create(OoParentWithoutPidEntity::class.java, OoChildWithPidEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
        
    @JvmField var _parentProperty: String? = null
    override val parentProperty: String
        get() = _parentProperty!!
                        
    override val childOne: OoChildWithPidEntity?
        get() = snapshot.extractOneToOneChild(CHILDONE_CONNECTION_ID, this)

    class Builder(val result: OoParentWithoutPidEntityData?): ModifiableWorkspaceEntityBase<OoParentWithoutPidEntity>(), OoParentWithoutPidEntity.Builder {
        constructor(): this(OoParentWithoutPidEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity OoParentWithoutPidEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            val __childOne = _childOne
            if (__childOne != null && __childOne is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__childOne)
                applyRef(CHILDONE_CONNECTION_ID, __childOne)
                this._childOne = null
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
            if (!getEntityData().isParentPropertyInitialized()) {
                error("Field OoParentWithoutPidEntity#parentProperty should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field OoParentWithoutPidEntity#entitySource should be initialized")
            }
        }
    
        
        override var parentProperty: String
            get() = getEntityData().parentProperty
            set(value) {
                checkModificationAllowed()
                getEntityData().parentProperty = value
                changedProperty.add("parentProperty")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        var _childOne: OoChildWithPidEntity? = null
        override var childOne: OoChildWithPidEntity?
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToOneChild(CHILDONE_CONNECTION_ID, this) ?: _childOne
                } else {
                    _childOne
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    // Back reference for a reference of non-ext field
                    if (value is OoChildWithPidEntityImpl.Builder) {
                        value._parentEntity = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToOneChildOfParent(CHILDONE_CONNECTION_ID, this, value)
                }
                else {
                    // Back reference for a reference of non-ext field
                    if (value is OoChildWithPidEntityImpl.Builder) {
                        value._parentEntity = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this._childOne = value
                }
                changedProperty.add("childOne")
            }
        
        override fun getEntityData(): OoParentWithoutPidEntityData = result ?: super.getEntityData() as OoParentWithoutPidEntityData
        override fun getEntityClass(): Class<OoParentWithoutPidEntity> = OoParentWithoutPidEntity::class.java
    }
}
    
class OoParentWithoutPidEntityData : WorkspaceEntityData<OoParentWithoutPidEntity>() {
    lateinit var parentProperty: String

    fun isParentPropertyInitialized(): Boolean = ::parentProperty.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<OoParentWithoutPidEntity> {
        val modifiable = OoParentWithoutPidEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): OoParentWithoutPidEntity {
        val entity = OoParentWithoutPidEntityImpl()
        entity._parentProperty = parentProperty
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return OoParentWithoutPidEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as OoParentWithoutPidEntityData
        
        if (this.parentProperty != other.parentProperty) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as OoParentWithoutPidEntityData
        
        if (this.parentProperty != other.parentProperty) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + parentProperty.hashCode()
        return result
    }
}