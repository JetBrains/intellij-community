package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import org.jetbrains.deft.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class OoParentWithPidEntityImpl: OoParentWithPidEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val CHILDONE_CONNECTION_ID: ConnectionId = ConnectionId.create(OoParentWithPidEntity::class.java, OoChildForParentWithPidEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        internal val CHILDTHREE_CONNECTION_ID: ConnectionId = ConnectionId.create(OoParentWithPidEntity::class.java, OoChildAlsoWithPidEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
    
    override val factory: ObjType<*, *>
        get() = OoParentWithPidEntity
        
    @JvmField var _parentProperty: String? = null
    override val parentProperty: String
        get() = _parentProperty!!
                        
    override val childOne: OoChildForParentWithPidEntity?
        get() = snapshot.extractOneToOneChild(CHILDONE_CONNECTION_ID, this)           
        
    override val childThree: OoChildAlsoWithPidEntity?
        get() = snapshot.extractOneToOneChild(CHILDTHREE_CONNECTION_ID, this)

    class Builder(val result: OoParentWithPidEntityData?): ModifiableWorkspaceEntityBase<OoParentWithPidEntity>(), OoParentWithPidEntity.Builder {
        constructor(): this(OoParentWithPidEntityData())
                 
        override val factory: ObjType<OoParentWithPidEntity, *> get() = TODO()
        override fun build(): OoParentWithPidEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity OoParentWithPidEntity is already created in a different builder")
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
            val __childThree = _childThree
            if (__childThree != null && __childThree is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__childThree)
                applyRef(CHILDTHREE_CONNECTION_ID, __childThree)
                this._childThree = null
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
                error("Field OoParentWithPidEntity#parentProperty should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field OoParentWithPidEntity#entitySource should be initialized")
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
            
            var _childOne: OoChildForParentWithPidEntity? = null
            override var childOne: OoChildForParentWithPidEntity?
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
                        if (value is OoChildForParentWithPidEntityImpl.Builder) {
                            value._parentEntity = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(CHILDONE_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is OoChildForParentWithPidEntityImpl.Builder) {
                            value._parentEntity = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._childOne = value
                    }
                    changedProperty.add("childOne")
                }
        
            var _childThree: OoChildAlsoWithPidEntity? = null
            override var childThree: OoChildAlsoWithPidEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneChild(CHILDTHREE_CONNECTION_ID, this) ?: _childThree
                    } else {
                        _childThree
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is OoChildAlsoWithPidEntityImpl.Builder) {
                            value._parentEntity = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(CHILDTHREE_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is OoChildAlsoWithPidEntityImpl.Builder) {
                            value._parentEntity = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._childThree = value
                    }
                    changedProperty.add("childThree")
                }
        
        override fun getEntityData(): OoParentWithPidEntityData = result ?: super.getEntityData() as OoParentWithPidEntityData
        override fun getEntityClass(): Class<OoParentWithPidEntity> = OoParentWithPidEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(OoParentWithPidEntityData())
}
    
class OoParentWithPidEntityData : WorkspaceEntityData.WithCalculablePersistentId<OoParentWithPidEntity>() {
    lateinit var parentProperty: String

    fun isParentPropertyInitialized(): Boolean = ::parentProperty.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<OoParentWithPidEntity> {
        val modifiable = OoParentWithPidEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): OoParentWithPidEntity {
        val entity = OoParentWithPidEntityImpl()
        entity._parentProperty = parentProperty
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun persistentId(): PersistentEntityId<*> {
        return OoParentEntityId(parentProperty)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as OoParentWithPidEntityData
        
        if (this.parentProperty != other.parentProperty) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as OoParentWithPidEntityData
        
        if (this.parentProperty != other.parentProperty) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + parentProperty.hashCode()
        return result
    }
}