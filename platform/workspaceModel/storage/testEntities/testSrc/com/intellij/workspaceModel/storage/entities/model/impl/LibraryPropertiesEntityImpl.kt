package com.intellij.workspaceModel.storage.entities.model.api

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
import org.jetbrains.deft.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class LibraryPropertiesEntityImpl: LibraryPropertiesEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val LIBRARY_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java, LibraryPropertiesEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
    
    override val factory: ObjType<*, *>
        get() = LibraryPropertiesEntity
        
    override val library: LibraryEntity
        get() = snapshot.extractOneToOneParent(LIBRARY_CONNECTION_ID, this)!!           
        
    @JvmField var _libraryType: String? = null
    override val libraryType: String
        get() = _libraryType!!
                        
    @JvmField var _propertiesXmlTag: String? = null
    override val propertiesXmlTag: String
        get() = _propertiesXmlTag!!

    class Builder(val result: LibraryPropertiesEntityData?): ModifiableWorkspaceEntityBase<LibraryPropertiesEntity>(), LibraryPropertiesEntity.Builder {
        constructor(): this(LibraryPropertiesEntityData())
                 
        override val factory: ObjType<LibraryPropertiesEntity, *> get() = TODO()
        override fun build(): LibraryPropertiesEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity LibraryPropertiesEntity is already created in a different builder")
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
                (__library as LibraryEntityImpl.Builder)._libraryProperties = null
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
            if (_diff != null) {
                if (_diff.extractOneToOneParent<WorkspaceEntityBase>(LIBRARY_CONNECTION_ID, this) == null) {
                    error("Field LibraryPropertiesEntity#library should be initialized")
                }
            }
            else {
                if (_library == null) {
                    error("Field LibraryPropertiesEntity#library should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field LibraryPropertiesEntity#entitySource should be initialized")
            }
            if (!getEntityData().isLibraryTypeInitialized()) {
                error("Field LibraryPropertiesEntity#libraryType should be initialized")
            }
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
                            value._libraryProperties = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(LIBRARY_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is LibraryEntityImpl.Builder) {
                            value._libraryProperties = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._library = value
                    }
                    changedProperty.add("library")
                }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var libraryType: String
            get() = getEntityData().libraryType
            set(value) {
                checkModificationAllowed()
                getEntityData().libraryType = value
                changedProperty.add("libraryType")
            }
            
        override var propertiesXmlTag: String?
            get() = getEntityData().propertiesXmlTag
            set(value) {
                checkModificationAllowed()
                getEntityData().propertiesXmlTag = value
                changedProperty.add("propertiesXmlTag")
            }
        
        override fun getEntityData(): LibraryPropertiesEntityData = result ?: super.getEntityData() as LibraryPropertiesEntityData
        override fun getEntityClass(): Class<LibraryPropertiesEntity> = LibraryPropertiesEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(LibraryPropertiesEntityData())
}
    
class LibraryPropertiesEntityData : WorkspaceEntityData<LibraryPropertiesEntity>() {
    lateinit var libraryType: String
    var propertiesXmlTag: String? = null

    fun isLibraryTypeInitialized(): Boolean = ::libraryType.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<LibraryPropertiesEntity> {
        val modifiable = LibraryPropertiesEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): LibraryPropertiesEntity {
        val entity = LibraryPropertiesEntityImpl()
        entity._libraryType = libraryType
        entity._propertiesXmlTag = propertiesXmlTag
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as LibraryPropertiesEntityData
        
        if (this.entitySource != other.entitySource) return false
        if (this.libraryType != other.libraryType) return false
        if (this.propertiesXmlTag != other.propertiesXmlTag) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as LibraryPropertiesEntityData
        
        if (this.libraryType != other.libraryType) return false
        if (this.propertiesXmlTag != other.propertiesXmlTag) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + libraryType.hashCode()
        result = 31 * result + propertiesXmlTag.hashCode()
        return result
    }
}