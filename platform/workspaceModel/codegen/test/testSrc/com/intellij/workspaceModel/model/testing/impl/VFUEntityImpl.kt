package com.intellij.workspace.model.testing

import com.intellij.workspaceModel.codegen.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class VFUEntityImpl: VFUEntity, WorkspaceEntityBase() {
    
    
    override val factory: ObjType<*, *>
        get() = VFUEntity
        
    @JvmField var _data: String? = null
    override val data: String
        get() = _data!!
                        
    @JvmField var _filePath: VirtualFileUrl? = null
    override val filePath: VirtualFileUrl?
        get() = _filePath
                        
    @JvmField var _directoryPath: VirtualFileUrl? = null
    override val directoryPath: VirtualFileUrl
        get() = _directoryPath!!
                        
    @JvmField var _notNullRoots: List<VirtualFileUrl>? = null
    override val notNullRoots: List<VirtualFileUrl>
        get() = _notNullRoots!!

    class Builder(val result: VFUEntityData?): ModifiableWorkspaceEntityBase<VFUEntity>(), VFUEntity.Builder {
        constructor(): this(VFUEntityData())
                 
        override val factory: ObjType<VFUEntity, *> get() = TODO()
        override fun build(): VFUEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity VFUEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            index(this, "filePath", this.filePath)
            index(this, "directoryPath", this.directoryPath)
            index(this, "notNullRoots", this.notNullRoots.toHashSet())
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
            if (!getEntityData().isDataInitialized()) {
                error("Field VFUEntity#data should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field VFUEntity#entitySource should be initialized")
            }
            if (!getEntityData().isDirectoryPathInitialized()) {
                error("Field VFUEntity#directoryPath should be initialized")
            }
            if (!getEntityData().isNotNullRootsInitialized()) {
                error("Field VFUEntity#notNullRoots should be initialized")
            }
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
            
        override var filePath: VirtualFileUrl?
            get() = getEntityData().filePath
            set(value) {
                checkModificationAllowed()
                getEntityData().filePath = value
                changedProperty.add("filePath")
                val _diff = diff
                if (_diff != null) index(this, "filePath", value)
            }
            
        override var directoryPath: VirtualFileUrl
            get() = getEntityData().directoryPath
            set(value) {
                checkModificationAllowed()
                getEntityData().directoryPath = value
                changedProperty.add("directoryPath")
                val _diff = diff
                if (_diff != null) index(this, "directoryPath", value)
            }
            
        override var notNullRoots: List<VirtualFileUrl>
            get() = getEntityData().notNullRoots
            set(value) {
                checkModificationAllowed()
                getEntityData().notNullRoots = value
                val _diff = diff
                if (_diff != null) index(this, "notNullRoots", value.toHashSet())
                changedProperty.add("notNullRoots")
            }
        
        override fun hasNewValue(field: Field<in VFUEntity, *>): Boolean = TODO("Not yet implemented")                                                                     
        override fun <V> setValue(field: Field<in VFUEntity, V>, value: V) = TODO("Not yet implemented")
        override fun getEntityData(): VFUEntityData = result ?: super.getEntityData() as VFUEntityData
        override fun getEntityClass(): Class<VFUEntity> = VFUEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(VFUEntityData())
}
    
class VFUEntityData : WorkspaceEntityData<VFUEntity>() {
    lateinit var data: String
    var filePath: VirtualFileUrl? = null
    lateinit var directoryPath: VirtualFileUrl
    lateinit var notNullRoots: List<VirtualFileUrl>

    fun isDataInitialized(): Boolean = ::data.isInitialized
    fun isDirectoryPathInitialized(): Boolean = ::directoryPath.isInitialized
    fun isNotNullRootsInitialized(): Boolean = ::notNullRoots.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<VFUEntity> {
        val modifiable = VFUEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): VFUEntity {
        val entity = VFUEntityImpl()
        entity._data = data
        entity._filePath = filePath
        entity._directoryPath = directoryPath
        entity._notNullRoots = notNullRoots
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as VFUEntityData
        
        if (this.data != other.data) return false
        if (this.entitySource != other.entitySource) return false
        if (this.filePath != other.filePath) return false
        if (this.directoryPath != other.directoryPath) return false
        if (this.notNullRoots != other.notNullRoots) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as VFUEntityData
        
        if (this.data != other.data) return false
        if (this.filePath != other.filePath) return false
        if (this.directoryPath != other.directoryPath) return false
        if (this.notNullRoots != other.notNullRoots) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + data.hashCode()
        result = 31 * result + filePath.hashCode()
        result = 31 * result + directoryPath.hashCode()
        result = 31 * result + notNullRoots.hashCode()
        return result
    }
}