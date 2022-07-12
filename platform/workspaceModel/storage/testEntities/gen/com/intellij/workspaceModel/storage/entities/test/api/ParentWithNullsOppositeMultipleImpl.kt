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
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ParentWithNullsOppositeMultipleImpl: ParentWithNullsOppositeMultiple, WorkspaceEntityBase() {
    
    companion object {
        
        
        val connections = listOf<ConnectionId>(
        )

    }
        
    @JvmField var _parentData: String? = null
    override val parentData: String
        get() = _parentData!!
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: ParentWithNullsOppositeMultipleData?): ModifiableWorkspaceEntityBase<ParentWithNullsOppositeMultiple>(), ParentWithNullsOppositeMultiple.Builder {
        constructor(): this(ParentWithNullsOppositeMultipleData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ParentWithNullsOppositeMultiple is already created in a different builder")
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
            if (!getEntityData().isParentDataInitialized()) {
                error("Field ParentWithNullsOppositeMultiple#parentData should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ParentWithNullsOppositeMultiple#entitySource should be initialized")
            }
        }
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
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
        
        override fun getEntityData(): ParentWithNullsOppositeMultipleData = result ?: super.getEntityData() as ParentWithNullsOppositeMultipleData
        override fun getEntityClass(): Class<ParentWithNullsOppositeMultiple> = ParentWithNullsOppositeMultiple::class.java
    }
}
    
class ParentWithNullsOppositeMultipleData : WorkspaceEntityData<ParentWithNullsOppositeMultiple>() {
    lateinit var parentData: String

    fun isParentDataInitialized(): Boolean = ::parentData.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ParentWithNullsOppositeMultiple> {
        val modifiable = ParentWithNullsOppositeMultipleImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ParentWithNullsOppositeMultiple {
        val entity = ParentWithNullsOppositeMultipleImpl()
        entity._parentData = parentData
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ParentWithNullsOppositeMultiple::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ParentWithNullsOppositeMultipleData
        
        if (this.parentData != other.parentData) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ParentWithNullsOppositeMultipleData
        
        if (this.parentData != other.parentData) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + parentData.hashCode()
        return result
    }
}