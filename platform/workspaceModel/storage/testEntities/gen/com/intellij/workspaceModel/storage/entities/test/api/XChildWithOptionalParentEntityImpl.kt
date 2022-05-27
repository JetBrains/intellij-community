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
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class XChildWithOptionalParentEntityImpl: XChildWithOptionalParentEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val OPTIONALPARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(XParentEntity::class.java, XChildWithOptionalParentEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
    }
        
    @JvmField var _childProperty: String? = null
    override val childProperty: String
        get() = _childProperty!!
                        
    override val optionalParent: XParentEntity?
        get() = snapshot.extractOneToManyParent(OPTIONALPARENT_CONNECTION_ID, this)

    class Builder(val result: XChildWithOptionalParentEntityData?): ModifiableWorkspaceEntityBase<XChildWithOptionalParentEntity>(), XChildWithOptionalParentEntity.Builder {
        constructor(): this(XChildWithOptionalParentEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity XChildWithOptionalParentEntity is already created in a different builder")
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
                val __mutOptionalChildren = (__optionalParent as XParentEntityImpl.Builder)._optionalChildren?.toMutableList()
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
                error("Field XChildWithOptionalParentEntity#childProperty should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field XChildWithOptionalParentEntity#entitySource should be initialized")
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
            
            var _optionalParent: XParentEntity? = null
            override var optionalParent: XParentEntity?
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
                        // Back reference for the list of non-ext field
                        if (value is XParentEntityImpl.Builder) {
                            value._optionalChildren = (value._optionalChildren ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToManyParentOfChild(OPTIONALPARENT_CONNECTION_ID, this, value)
                    }
                    else {
                        // Back reference for the list of non-ext field
                        if (value is XParentEntityImpl.Builder) {
                            value._optionalChildren = (value._optionalChildren ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._optionalParent = value
                    }
                    changedProperty.add("optionalParent")
                }
        
        override fun getEntityData(): XChildWithOptionalParentEntityData = result ?: super.getEntityData() as XChildWithOptionalParentEntityData
        override fun getEntityClass(): Class<XChildWithOptionalParentEntity> = XChildWithOptionalParentEntity::class.java
    }
}
    
class XChildWithOptionalParentEntityData : WorkspaceEntityData<XChildWithOptionalParentEntity>() {
    lateinit var childProperty: String

    fun isChildPropertyInitialized(): Boolean = ::childProperty.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<XChildWithOptionalParentEntity> {
        val modifiable = XChildWithOptionalParentEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): XChildWithOptionalParentEntity {
        val entity = XChildWithOptionalParentEntityImpl()
        entity._childProperty = childProperty
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return XChildWithOptionalParentEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as XChildWithOptionalParentEntityData
        
        if (this.childProperty != other.childProperty) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as XChildWithOptionalParentEntityData
        
        if (this.childProperty != other.childProperty) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + childProperty.hashCode()
        return result
    }
}