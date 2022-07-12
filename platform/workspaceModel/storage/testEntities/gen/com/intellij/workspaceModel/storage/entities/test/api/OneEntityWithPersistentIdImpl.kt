package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.annotations.Open

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class OneEntityWithPersistentIdImpl: OneEntityWithPersistentId, WorkspaceEntityBase() {
    
    companion object {
        
        
        val connections = listOf<ConnectionId>(
        )

    }
        
    @JvmField var _myName: String? = null
    override val myName: String
        get() = _myName!!
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: OneEntityWithPersistentIdData?): ModifiableWorkspaceEntityBase<OneEntityWithPersistentId>(), OneEntityWithPersistentId.Builder {
        constructor(): this(OneEntityWithPersistentIdData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
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
            
            // Process linked entities that are connected without a builder
            processLinkedEntities(builder)
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
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
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
}
    
class OneEntityWithPersistentIdData : WorkspaceEntityData.WithCalculablePersistentId<OneEntityWithPersistentId>() {
    lateinit var myName: String

    fun isMyNameInitialized(): Boolean = ::myName.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<OneEntityWithPersistentId> {
        val modifiable = OneEntityWithPersistentIdImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): OneEntityWithPersistentId {
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

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return OneEntityWithPersistentId::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
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