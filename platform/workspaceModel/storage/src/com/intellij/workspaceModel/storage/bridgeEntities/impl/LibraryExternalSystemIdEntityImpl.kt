package com.intellij.workspaceModel.storage.bridgeEntities.api

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
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import org.jetbrains.deft.ObjBuilder

    

open class LibraryExternalSystemIdEntityImpl: LibraryExternalSystemIdEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val LIBRARY_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java, LibraryExternalSystemIdEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
        
    @JvmField var _externalSystemId: String? = null
    override val externalSystemId: String
        get() = _externalSystemId!!
                        
    override val library: LibraryEntity
        get() = snapshot.extractOneToOneParent(LIBRARY_CONNECTION_ID, this)!!

    class Builder(val result: LibraryExternalSystemIdEntityData?): ModifiableWorkspaceEntityBase<LibraryExternalSystemIdEntity>(), LibraryExternalSystemIdEntity.Builder {
        constructor(): this(LibraryExternalSystemIdEntityData())
                 
        override fun build(): LibraryExternalSystemIdEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity LibraryExternalSystemIdEntity is already created in a different builder")
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
            val __library = _library
            if (__library != null && (__library is ModifiableWorkspaceEntityBase<*>) && __library.diff == null) {
                builder.addEntity(__library)
            }
            if (__library != null && (__library is ModifiableWorkspaceEntityBase<*>) && __library.diff != null) {
                // Set field to null (in referenced entity)
                __library.extReferences.remove(ExtRefKey("LibraryExternalSystemIdEntity", "library", true, LIBRARY_CONNECTION_ID))
            }
            if (__library != null) {
                applyParentRef(LIBRARY_CONNECTION_ID, __library)
                this._library = null
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
            if (!getEntityData().isExternalSystemIdInitialized()) {
                error("Field LibraryExternalSystemIdEntity#externalSystemId should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field LibraryExternalSystemIdEntity#entitySource should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToOneParent<WorkspaceEntityBase>(LIBRARY_CONNECTION_ID, this) == null) {
                    error("Field LibraryExternalSystemIdEntity#library should be initialized")
                }
            }
            else {
                if (_library == null) {
                    error("Field LibraryExternalSystemIdEntity#library should be initialized")
                }
            }
        }
    
        
        override var externalSystemId: String
            get() = getEntityData().externalSystemId
            set(value) {
                checkModificationAllowed()
                getEntityData().externalSystemId = value
                changedProperty.add("externalSystemId")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
            var _library: LibraryEntity? = null
            override var library: LibraryEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneParent(LIBRARY_CONNECTION_ID, this) ?: _library!!
                    } else {
                        _library!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is LibraryEntityImpl.Builder) {
                            value.extReferences[ExtRefKey("LibraryExternalSystemIdEntity", "library", true, LIBRARY_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(LIBRARY_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is LibraryEntityImpl.Builder) {
                            value.extReferences[ExtRefKey("LibraryExternalSystemIdEntity", "library", true, LIBRARY_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._library = value
                    }
                    changedProperty.add("library")
                }
        
        override fun getEntityData(): LibraryExternalSystemIdEntityData = result ?: super.getEntityData() as LibraryExternalSystemIdEntityData
        override fun getEntityClass(): Class<LibraryExternalSystemIdEntity> = LibraryExternalSystemIdEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(LibraryExternalSystemIdEntityData())
}
    
class LibraryExternalSystemIdEntityData : WorkspaceEntityData<LibraryExternalSystemIdEntity>() {
    lateinit var externalSystemId: String

    fun isExternalSystemIdInitialized(): Boolean = ::externalSystemId.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<LibraryExternalSystemIdEntity> {
        val modifiable = LibraryExternalSystemIdEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): LibraryExternalSystemIdEntity {
        val entity = LibraryExternalSystemIdEntityImpl()
        entity._externalSystemId = externalSystemId
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as LibraryExternalSystemIdEntityData
        
        if (this.externalSystemId != other.externalSystemId) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as LibraryExternalSystemIdEntityData
        
        if (this.externalSystemId != other.externalSystemId) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + externalSystemId.hashCode()
        return result
    }
}