package com.intellij.workspaceModel.storage.bridgeEntities.api

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
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import java.io.Serializable
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class LibraryPropertiesEntityImpl: LibraryPropertiesEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val LIBRARY_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java, LibraryPropertiesEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        
        val connections = listOf<ConnectionId>(
            LIBRARY_CONNECTION_ID,
        )

    }
        
    override val library: LibraryEntity
        get() = snapshot.extractOneToOneParent(LIBRARY_CONNECTION_ID, this)!!           
        
    @JvmField var _libraryType: String? = null
    override val libraryType: String
        get() = _libraryType!!
                        
    @JvmField var _propertiesXmlTag: String? = null
    override val propertiesXmlTag: String?
        get() = _propertiesXmlTag
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: LibraryPropertiesEntityData?): ModifiableWorkspaceEntityBase<LibraryPropertiesEntity>(), LibraryPropertiesEntity.Builder {
        constructor(): this(LibraryPropertiesEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
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
            
            // Process linked entities that are connected without a builder
            processLinkedEntities(builder)
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
                if (this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)] == null) {
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
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }
    
        
        override var library: LibraryEntity
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToOneParent(LIBRARY_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)]!! as LibraryEntity
                } else {
                    this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)]!! as LibraryEntity
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(true, LIBRARY_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToOneParentOfChild(LIBRARY_CONNECTION_ID, this, value)
                }
                else {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(true, LIBRARY_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)] = value
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
}
    
class LibraryPropertiesEntityData : WorkspaceEntityData<LibraryPropertiesEntity>() {
    lateinit var libraryType: String
    var propertiesXmlTag: String? = null

    fun isLibraryTypeInitialized(): Boolean = ::libraryType.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<LibraryPropertiesEntity> {
        val modifiable = LibraryPropertiesEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): LibraryPropertiesEntity {
        val entity = LibraryPropertiesEntityImpl()
        entity._libraryType = libraryType
        entity._propertiesXmlTag = propertiesXmlTag
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return LibraryPropertiesEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
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