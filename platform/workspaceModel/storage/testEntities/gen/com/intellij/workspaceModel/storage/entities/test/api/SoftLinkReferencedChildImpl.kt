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
import org.jetbrains.deft.annotations.Open

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class SoftLinkReferencedChildImpl: SoftLinkReferencedChild, WorkspaceEntityBase() {
    
    companion object {
        internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(EntityWithSoftLinks::class.java, SoftLinkReferencedChild::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    }
        
    override val parentEntity: EntityWithSoftLinks
        get() = snapshot.extractOneToManyParent(PARENTENTITY_CONNECTION_ID, this)!!

    class Builder(val result: SoftLinkReferencedChildData?): ModifiableWorkspaceEntityBase<SoftLinkReferencedChild>(), SoftLinkReferencedChild.Builder {
        constructor(): this(SoftLinkReferencedChildData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity SoftLinkReferencedChild is already created in a different builder")
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
            val __parentEntity = _parentEntity
            if (__parentEntity != null && (__parentEntity is ModifiableWorkspaceEntityBase<*>) && __parentEntity.diff == null) {
                builder.addEntity(__parentEntity)
            }
            if (__parentEntity != null && (__parentEntity is ModifiableWorkspaceEntityBase<*>) && __parentEntity.diff != null) {
                // Set field to null (in referenced entity)
                val __mutChildren = (__parentEntity as EntityWithSoftLinksImpl.Builder)._children?.toMutableList()
                __mutChildren?.remove(this)
                __parentEntity._children = if (__mutChildren.isNullOrEmpty()) emptyList() else __mutChildren
            }
            if (__parentEntity != null) {
                applyParentRef(PARENTENTITY_CONNECTION_ID, __parentEntity)
                this._parentEntity = null
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
            if (_diff != null) {
                if (_diff.extractOneToManyParent<WorkspaceEntityBase>(PARENTENTITY_CONNECTION_ID, this) == null) {
                    error("Field SoftLinkReferencedChild#parentEntity should be initialized")
                }
            }
            else {
                if (_parentEntity == null) {
                    error("Field SoftLinkReferencedChild#parentEntity should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field SoftLinkReferencedChild#entitySource should be initialized")
            }
        }
    
        
        var _parentEntity: EntityWithSoftLinks? = null
        override var parentEntity: EntityWithSoftLinks
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToManyParent(PARENTENTITY_CONNECTION_ID, this) ?: _parentEntity!!
                } else {
                    _parentEntity!!
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    // Back reference for the list of non-ext field
                    if (value is EntityWithSoftLinksImpl.Builder) {
                        value._children = (value._children ?: emptyList()) + this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToManyParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
                }
                else {
                    // Back reference for the list of non-ext field
                    if (value is EntityWithSoftLinksImpl.Builder) {
                        value._children = (value._children ?: emptyList()) + this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this._parentEntity = value
                }
                changedProperty.add("parentEntity")
            }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
        
        override fun getEntityData(): SoftLinkReferencedChildData = result ?: super.getEntityData() as SoftLinkReferencedChildData
        override fun getEntityClass(): Class<SoftLinkReferencedChild> = SoftLinkReferencedChild::class.java
    }
}
    
class SoftLinkReferencedChildData : WorkspaceEntityData<SoftLinkReferencedChild>() {


    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<SoftLinkReferencedChild> {
        val modifiable = SoftLinkReferencedChildImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): SoftLinkReferencedChild {
        val entity = SoftLinkReferencedChildImpl()
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return SoftLinkReferencedChild::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SoftLinkReferencedChildData
        
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SoftLinkReferencedChildData
        
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        return result
    }
}