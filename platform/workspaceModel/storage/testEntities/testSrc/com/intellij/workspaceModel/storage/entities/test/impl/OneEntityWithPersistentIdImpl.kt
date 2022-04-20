package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import org.jetbrains.deft.ObjBuilder

    

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class OneEntityWithPersistentIdImpl: OneEntityWithPersistentId, WorkspaceEntityBase() {
    
        
    @JvmField var _myName: String? = null
    override val myName: String
        get() = _myName!!

    class Builder(val result: OneEntityWithPersistentIdData?): ModifiableWorkspaceEntityBase<OneEntityWithPersistentId>(), OneEntityWithPersistentId.Builder {
        constructor(): this(OneEntityWithPersistentIdData())
                 
        override fun build(): OneEntityWithPersistentId = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity OneEntityWithPersistentId is already created in a different builder")
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
            if (!getEntityData().isMyNameInitialized()) {
                error("Field OneEntityWithPersistentId#myName should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field OneEntityWithPersistentId#entitySource should be initialized")
            }
        }
    
        
        override var myName: String
            get() = getEntityData().myName
            set(value) {
                checkModificationAllowed()
                getEntityData().myName = value
                changedProperty.add("myName")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
        
        override fun getEntityData(): OneEntityWithPersistentIdData = result ?: super.getEntityData() as OneEntityWithPersistentIdData
        override fun getEntityClass(): Class<OneEntityWithPersistentId> = OneEntityWithPersistentId::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(OneEntityWithPersistentIdData())
}
    
class OneEntityWithPersistentIdData : WorkspaceEntityData.WithCalculablePersistentId<OneEntityWithPersistentId>() {
    lateinit var myName: String

    fun isMyNameInitialized(): Boolean = ::myName.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<OneEntityWithPersistentId> {
        val modifiable = OneEntityWithPersistentIdImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): OneEntityWithPersistentId {
        val entity = OneEntityWithPersistentIdImpl()
        entity._myName = myName
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun persistentId(): PersistentEntityId<*> {
        
            return OnePersistentId(myName)
        
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as OneEntityWithPersistentIdData
        
        if (this.myName != other.myName) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as OneEntityWithPersistentIdData
        
        if (this.myName != other.myName) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + myName.hashCode()
        return result
    }
}