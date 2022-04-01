package com.intellij.workspace.model.testing

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
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
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class ParentSubEntityImpl: ParentSubEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val CHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentSubEntity::class.java, ChildSubEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
    
    override val factory: ObjType<*, *>
        get() = ParentSubEntity
        
    @JvmField var _parentData: String? = null
    override val parentData: String
        get() = _parentData!!
                        
    override val child: ChildSubEntity
        get() = snapshot.extractOneToOneChild(CHILD_CONNECTION_ID, this)!!

    class Builder(val result: ParentSubEntityData?): ModifiableWorkspaceEntityBase<ParentSubEntity>(), ParentSubEntity.Builder {
        constructor(): this(ParentSubEntityData())
                 
        override val factory: ObjType<ParentSubEntity, *> get() = TODO()
        override fun build(): ParentSubEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
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
            
            val __child = _child
            if (__child != null && __child is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__child)
                applyRef(CHILD_CONNECTION_ID, __child)
                this._child = null
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
                if (_child == null) {
                    error("Field ParentSubEntity#child should be initialized")
                }
            }
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
            
            var _child: ChildSubEntity? = null
            override var child: ChildSubEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneChild(CHILD_CONNECTION_ID, this) ?: _child!!
                    } else {
                        _child!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ChildSubEntityImpl.Builder) {
                            value._parentEntity = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(CHILD_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ChildSubEntityImpl.Builder) {
                            value._parentEntity = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._child = value
                    }
                    changedProperty.add("child")
                }
        
        override fun getEntityData(): ParentSubEntityData = result ?: super.getEntityData() as ParentSubEntityData
        override fun getEntityClass(): Class<ParentSubEntity> = ParentSubEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(ParentSubEntityData())
}
    
class ParentSubEntityData : WorkspaceEntityData<ParentSubEntity>() {
    lateinit var parentData: String

    fun isParentDataInitialized(): Boolean = ::parentData.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<ParentSubEntity> {
        val modifiable = ParentSubEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): ParentSubEntity {
        val entity = ParentSubEntityImpl()
        entity._parentData = parentData
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
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