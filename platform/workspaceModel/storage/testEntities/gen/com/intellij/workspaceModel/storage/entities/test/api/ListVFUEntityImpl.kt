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
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ListVFUEntityImpl: ListVFUEntity, WorkspaceEntityBase() {
    
    companion object {
        
        
        val connections = listOf<ConnectionId>(
        )

    }
        
    @JvmField var _data: String? = null
    override val data: String
        get() = _data!!
                        
    @JvmField var _fileProperty: List<VirtualFileUrl>? = null
    override val fileProperty: List<VirtualFileUrl>
        get() = _fileProperty!!
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: ListVFUEntityData?): ModifiableWorkspaceEntityBase<ListVFUEntity>(), ListVFUEntity.Builder {
        constructor(): this(ListVFUEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ListVFUEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            index(this, "fileProperty", this.fileProperty.toHashSet())
            // Process linked entities that are connected without a builder
            processLinkedEntities(builder)
            checkInitialization() // TODO uncomment and check failed tests
        }
    
        fun checkInitialization() {
            val _diff = diff
            if (!getEntityData().isDataInitialized()) {
                error("Field ListVFUEntity#data should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ListVFUEntity#entitySource should be initialized")
            }
            if (!getEntityData().isFilePropertyInitialized()) {
                error("Field ListVFUEntity#fileProperty should be initialized")
            }
        }
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }
    
        
        override var data: String
            get() = getEntityData().data
            set(value) {
                checkModificationAllowed()
                getEntityData().data = value
                changedProperty.add("data")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var fileProperty: List<VirtualFileUrl>
            get() = getEntityData().fileProperty
            set(value) {
                checkModificationAllowed()
                getEntityData().fileProperty = value
                val _diff = diff
                if (_diff != null) index(this, "fileProperty", value.toHashSet())
                changedProperty.add("fileProperty")
            }
        
        override fun getEntityData(): ListVFUEntityData = result ?: super.getEntityData() as ListVFUEntityData
        override fun getEntityClass(): Class<ListVFUEntity> = ListVFUEntity::class.java
    }
}
    
class ListVFUEntityData : WorkspaceEntityData<ListVFUEntity>() {
    lateinit var data: String
    lateinit var fileProperty: List<VirtualFileUrl>

    fun isDataInitialized(): Boolean = ::data.isInitialized
    fun isFilePropertyInitialized(): Boolean = ::fileProperty.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ListVFUEntity> {
        val modifiable = ListVFUEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ListVFUEntity {
        val entity = ListVFUEntityImpl()
        entity._data = data
        entity._fileProperty = fileProperty
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ListVFUEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ListVFUEntityData
        
        if (this.data != other.data) return false
        if (this.entitySource != other.entitySource) return false
        if (this.fileProperty != other.fileProperty) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ListVFUEntityData
        
        if (this.data != other.data) return false
        if (this.fileProperty != other.fileProperty) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + data.hashCode()
        result = 31 * result + fileProperty.hashCode()
        return result
    }
}