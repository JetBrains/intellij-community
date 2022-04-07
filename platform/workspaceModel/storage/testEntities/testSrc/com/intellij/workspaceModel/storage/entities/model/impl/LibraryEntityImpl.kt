package com.intellij.workspaceModel.storage.entities.model.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class LibraryEntityImpl: LibraryEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val SDK_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java, SdkEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        internal val LIBRARYPROPERTIES_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java, LibraryPropertiesEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        internal val LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java, LibraryFilesPackagingElementEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, true)
    }
    
    override val factory: ObjType<*, *>
        get() = LibraryEntity
        
    @JvmField var _name: String? = null
    override val name: String
        get() = _name!!
                        
    @JvmField var _tableId: LibraryTableId? = null
    override val tableId: LibraryTableId
        get() = _tableId!!
                        
    @JvmField var _roots: List<LibraryRoot>? = null
    override val roots: List<LibraryRoot>
        get() = _roots!!   
    
    @JvmField var _excludedRoots: List<VirtualFileUrl>? = null
    override val excludedRoots: List<VirtualFileUrl>
        get() = _excludedRoots!!   
    
    override val sdk: SdkEntity?
        get() = snapshot.extractOneToOneChild(SDK_CONNECTION_ID, this)           
        
    override val libraryProperties: LibraryPropertiesEntity?
        get() = snapshot.extractOneToOneChild(LIBRARYPROPERTIES_CONNECTION_ID, this)           
        
    override val libraryFilesPackagingElement: LibraryFilesPackagingElementEntity?
        get() = snapshot.extractOneToOneChild(LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID, this)

    class Builder(val result: LibraryEntityData?): ModifiableWorkspaceEntityBase<LibraryEntity>(), LibraryEntity.Builder {
        constructor(): this(LibraryEntityData())
                 
        override val factory: ObjType<LibraryEntity, *> get() = TODO()
        override fun build(): LibraryEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity LibraryEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            index(this, "excludedRoots", this.excludedRoots.toHashSet())
            val __sdk = _sdk
            if (__sdk != null && __sdk is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__sdk)
                applyRef(SDK_CONNECTION_ID, __sdk)
                this._sdk = null
            }
            val __libraryProperties = _libraryProperties
            if (__libraryProperties != null && __libraryProperties is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__libraryProperties)
                applyRef(LIBRARYPROPERTIES_CONNECTION_ID, __libraryProperties)
                this._libraryProperties = null
            }
            val __libraryFilesPackagingElement = _libraryFilesPackagingElement
            if (__libraryFilesPackagingElement != null && __libraryFilesPackagingElement is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__libraryFilesPackagingElement)
                applyRef(LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID, __libraryFilesPackagingElement)
                this._libraryFilesPackagingElement = null
            }
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
            if (!getEntityData().isNameInitialized()) {
                error("Field LibraryEntity#name should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field LibraryEntity#entitySource should be initialized")
            }
            if (!getEntityData().isTableIdInitialized()) {
                error("Field LibraryEntity#tableId should be initialized")
            }
            if (!getEntityData().isRootsInitialized()) {
                error("Field LibraryEntity#roots should be initialized")
            }
            if (!getEntityData().isExcludedRootsInitialized()) {
                error("Field LibraryEntity#excludedRoots should be initialized")
            }
        }
    
        
        override var name: String
            get() = getEntityData().name
            set(value) {
                checkModificationAllowed()
                getEntityData().name = value
                changedProperty.add("name")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var tableId: LibraryTableId
            get() = getEntityData().tableId
            set(value) {
                checkModificationAllowed()
                getEntityData().tableId = value
                changedProperty.add("tableId")
                
            }
            
        override var roots: List<LibraryRoot>
            get() = getEntityData().roots
            set(value) {
                checkModificationAllowed()
                getEntityData().roots = value
                
                val _diff = diff
                if (_diff != null) {
                    val jarDirectories = mutableSetOf<VirtualFileUrl>()
                    val libraryRootList = value.map {
                        if (it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF) {
                            jarDirectories.add(it.url)
                        }
                        it.url
                    }.toHashSet()
                    index(this, "roots", libraryRootList)
                    indexJarDirectories(this, jarDirectories)
                }
        
                changedProperty.add("roots")
            }
            
        override var excludedRoots: List<VirtualFileUrl>
            get() = getEntityData().excludedRoots
            set(value) {
                checkModificationAllowed()
                getEntityData().excludedRoots = value
                val _diff = diff
                if (_diff != null) index(this, "excludedRoots", value.toHashSet())
                changedProperty.add("excludedRoots")
            }
            
            var _sdk: SdkEntity? = null
            override var sdk: SdkEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneChild(SDK_CONNECTION_ID, this) ?: _sdk
                    } else {
                        _sdk
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is SdkEntityImpl.Builder) {
                            value._library = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(SDK_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is SdkEntityImpl.Builder) {
                            value._library = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._sdk = value
                    }
                    changedProperty.add("sdk")
                }
        
            var _libraryProperties: LibraryPropertiesEntity? = null
            override var libraryProperties: LibraryPropertiesEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneChild(LIBRARYPROPERTIES_CONNECTION_ID, this) ?: _libraryProperties
                    } else {
                        _libraryProperties
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is LibraryPropertiesEntityImpl.Builder) {
                            value._library = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(LIBRARYPROPERTIES_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is LibraryPropertiesEntityImpl.Builder) {
                            value._library = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._libraryProperties = value
                    }
                    changedProperty.add("libraryProperties")
                }
        
            var _libraryFilesPackagingElement: LibraryFilesPackagingElementEntity? = null
            override var libraryFilesPackagingElement: LibraryFilesPackagingElementEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneChild(LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID, this) ?: _libraryFilesPackagingElement
                    } else {
                        _libraryFilesPackagingElement
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is LibraryFilesPackagingElementEntityImpl.Builder) {
                            value._library = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is LibraryFilesPackagingElementEntityImpl.Builder) {
                            value._library = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._libraryFilesPackagingElement = value
                    }
                    changedProperty.add("libraryFilesPackagingElement")
                }
        
        override fun getEntityData(): LibraryEntityData = result ?: super.getEntityData() as LibraryEntityData
        override fun getEntityClass(): Class<LibraryEntity> = LibraryEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(LibraryEntityData())
}
    
class LibraryEntityData : WorkspaceEntityData.WithCalculablePersistentId<LibraryEntity>() {
    lateinit var name: String
    lateinit var tableId: LibraryTableId
    lateinit var roots: List<LibraryRoot>
    lateinit var excludedRoots: List<VirtualFileUrl>

    fun isNameInitialized(): Boolean = ::name.isInitialized
    fun isTableIdInitialized(): Boolean = ::tableId.isInitialized
    fun isRootsInitialized(): Boolean = ::roots.isInitialized
    fun isExcludedRootsInitialized(): Boolean = ::excludedRoots.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<LibraryEntity> {
        val modifiable = LibraryEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): LibraryEntity {
        val entity = LibraryEntityImpl()
        entity._name = name
        entity._tableId = tableId
        entity._roots = roots
        entity._excludedRoots = excludedRoots
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun persistentId(): PersistentEntityId<*> {
        return LibraryId(name, tableId)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as LibraryEntityData
        
        if (this.name != other.name) return false
        if (this.entitySource != other.entitySource) return false
        if (this.tableId != other.tableId) return false
        if (this.roots != other.roots) return false
        if (this.excludedRoots != other.excludedRoots) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as LibraryEntityData
        
        if (this.name != other.name) return false
        if (this.tableId != other.tableId) return false
        if (this.roots != other.roots) return false
        if (this.excludedRoots != other.excludedRoots) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + tableId.hashCode()
        result = 31 * result + roots.hashCode()
        result = 31 * result + excludedRoots.hashCode()
        return result
    }
}