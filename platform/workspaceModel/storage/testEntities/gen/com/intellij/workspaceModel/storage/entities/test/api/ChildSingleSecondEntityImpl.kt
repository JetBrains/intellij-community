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
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractOneParentOfChild
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class ChildSingleSecondEntityImpl: ChildSingleSecondEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentSingleAbEntity::class.java, ChildSingleAbstractBaseEntity::class.java, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false)
    }
        
    @JvmField var _commonData: String? = null
    override val commonData: String
        get() = _commonData!!
                        
    override val parentEntity: ParentSingleAbEntity
        get() = snapshot.extractOneToAbstractOneParent(PARENTENTITY_CONNECTION_ID, this)!!           
        
    @JvmField var _secondData: String? = null
    override val secondData: String
        get() = _secondData!!

    class Builder(val result: ChildSingleSecondEntityData?): ModifiableWorkspaceEntityBase<ChildSingleSecondEntity>(), ChildSingleSecondEntity.Builder {
        constructor(): this(ChildSingleSecondEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ChildSingleSecondEntity is already created in a different builder")
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
                (__parentEntity as ParentSingleAbEntityImpl.Builder)._child = null
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
            if (!getEntityData().isCommonDataInitialized()) {
                error("Field ChildSingleAbstractBaseEntity#commonData should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToAbstractOneParent<WorkspaceEntityBase>(PARENTENTITY_CONNECTION_ID, this) == null) {
                    error("Field ChildSingleAbstractBaseEntity#parentEntity should be initialized")
                }
            }
            else {
                if (_parentEntity == null) {
                    error("Field ChildSingleAbstractBaseEntity#parentEntity should be initialized")
                }
            }
            if (!getEntityData().isSecondDataInitialized()) {
                error("Field ChildSingleSecondEntity#secondData should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ChildSingleSecondEntity#entitySource should be initialized")
            }
        }
    
        
        override var commonData: String
            get() = getEntityData().commonData
            set(value) {
                checkModificationAllowed()
                getEntityData().commonData = value
                changedProperty.add("commonData")
            }
            
        var _parentEntity: ParentSingleAbEntity? = null
        override var parentEntity: ParentSingleAbEntity
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToAbstractOneParent(PARENTENTITY_CONNECTION_ID, this) ?: _parentEntity!!
                } else {
                    _parentEntity!!
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    // Back reference for a reference of non-ext field
                    if (value is ParentSingleAbEntityImpl.Builder) {
                        value._child = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToAbstractOneParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
                }
                else {
                    // Back reference for a reference of non-ext field
                    if (value is ParentSingleAbEntityImpl.Builder) {
                        value._child = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this._parentEntity = value
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
        
        override fun getEntityData(): ChildSingleSecondEntityData = result ?: super.getEntityData() as ChildSingleSecondEntityData
        override fun getEntityClass(): Class<ChildSingleSecondEntity> = ChildSingleSecondEntity::class.java
    }
}
    
class ChildSingleSecondEntityData : WorkspaceEntityData<ChildSingleSecondEntity>() {
    lateinit var commonData: String
    lateinit var secondData: String

    fun isCommonDataInitialized(): Boolean = ::commonData.isInitialized
    fun isSecondDataInitialized(): Boolean = ::secondData.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ChildSingleSecondEntity> {
        val modifiable = ChildSingleSecondEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ChildSingleSecondEntity {
        val entity = ChildSingleSecondEntityImpl()
        entity._commonData = commonData
        entity._secondData = secondData
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ChildSingleSecondEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildSingleSecondEntityData
        
        if (this.commonData != other.commonData) return false
        if (this.secondData != other.secondData) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildSingleSecondEntityData
        
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