package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class ChildWithOptionalParentEntityImpl: ChildWithOptionalParentEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val OPTIONALPARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentEntity::class.java, ChildWithOptionalParentEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
    }
    
    override val factory: ObjType<*, *>
        get() = ChildWithOptionalParentEntity
        
    @JvmField var _childProperty: String? = null
    override val childProperty: String
        get() = _childProperty!!
                        
    override val optionalParent: ParentEntity?
        get() = snapshot.extractOneToManyParent(OPTIONALPARENT_CONNECTION_ID, this)

    class Builder(val result: ChildWithOptionalParentEntityData?): ModifiableWorkspaceEntityBase<ChildWithOptionalParentEntity>(), ChildWithOptionalParentEntity.Builder {
        constructor(): this(ChildWithOptionalParentEntityData())
                 
        override val factory: ObjType<ChildWithOptionalParentEntity, *> get() = TODO()
        override fun build(): ChildWithOptionalParentEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ChildWithOptionalParentEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
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
            val __optionalParent = _optionalParent
            if (__optionalParent != null && (__optionalParent is ModifiableWorkspaceEntityBase<*>) && __optionalParent.diff == null) {
                builder.addEntity(__optionalParent)
            }
            if (__optionalParent != null && (__optionalParent is ModifiableWorkspaceEntityBase<*>) && __optionalParent.diff != null) {
                // Set field to null (in referenced entity)
                val __mutOptionalChildren = (__optionalParent as ParentEntityImpl.Builder)._optionalChildren?.toMutableList()
                __mutOptionalChildren?.remove(this)
                __optionalParent._optionalChildren = if (__mutOptionalChildren.isNullOrEmpty()) null else __mutOptionalChildren
            }
            if (__optionalParent != null) {
                applyParentRef(OPTIONALPARENT_CONNECTION_ID, __optionalParent)
                this._optionalParent = null
            }
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
            if (!getEntityData().isChildPropertyInitialized()) {
                error("Field ChildWithOptionalParentEntity#childProperty should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ChildWithOptionalParentEntity#entitySource should be initialized")
            }
        }
    
        
        override var childProperty: String
            get() = getEntityData().childProperty
            set(value) {
                checkModificationAllowed()
                getEntityData().childProperty = value
                changedProperty.add("childProperty")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
            var _optionalParent: ParentEntity? = null
            override var optionalParent: ParentEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyParent(OPTIONALPARENT_CONNECTION_ID, this) ?: _optionalParent
                    } else {
                        _optionalParent
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ParentEntityImpl.Builder) {
                            value._optionalChildren = (value._optionalChildren ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToManyParentOfChild(OPTIONALPARENT_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ParentEntityImpl.Builder) {
                            value._optionalChildren = (value._optionalChildren ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._optionalParent = value
                    }
                    changedProperty.add("optionalParent")
                }
        
        override fun getEntityData(): ChildWithOptionalParentEntityData = result ?: super.getEntityData() as ChildWithOptionalParentEntityData
        override fun getEntityClass(): Class<ChildWithOptionalParentEntity> = ChildWithOptionalParentEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(ChildWithOptionalParentEntityData())
}
    
class ChildWithOptionalParentEntityData : WorkspaceEntityData<ChildWithOptionalParentEntity>() {
    lateinit var childProperty: String

    fun isChildPropertyInitialized(): Boolean = ::childProperty.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<ChildWithOptionalParentEntity> {
        val modifiable = ChildWithOptionalParentEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): ChildWithOptionalParentEntity {
        val entity = ChildWithOptionalParentEntityImpl()
        entity._childProperty = childProperty
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildWithOptionalParentEntityData
        
        if (this.childProperty != other.childProperty) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildWithOptionalParentEntityData
        
        if (this.childProperty != other.childProperty) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + childProperty.hashCode()
        return result
    }
}