package com.intellij.workspaceModel.storage.bridgeEntities.api

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
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.SoftLinkable
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import java.io.Serializable
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class LibraryEntityImpl: LibraryEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val SDK_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java, SdkEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        internal val LIBRARYPROPERTIES_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java, LibraryPropertiesEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        internal val LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java, LibraryFilesPackagingElementEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        
        val connections = listOf<ConnectionId>(
            SDK_CONNECTION_ID,
            LIBRARYPROPERTIES_CONNECTION_ID,
            LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID,
        )

    }
        
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
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: LibraryEntityData?): ModifiableWorkspaceEntityBase<LibraryEntity>(), LibraryEntity.Builder {
        constructor(): this(LibraryEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
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
            indexLibraryRoots(roots)
            // Process linked entities that are connected without a builder
            processLinkedEntities(builder)
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
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }
        private fun indexLibraryRoots(libraryRoots: List<LibraryRoot>) {
            val jarDirectories = mutableSetOf<VirtualFileUrl>()
            val libraryRootList = libraryRoots.map {
              if (it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF) {
                jarDirectories.add(it.url)
              }
              it.url
            }.toHashSet()
            index(this, "roots", libraryRootList)
            indexJarDirectories(this, jarDirectories)
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
                    indexLibraryRoots(value)
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
            
        override var sdk: SdkEntity?
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToOneChild(SDK_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true, SDK_CONNECTION_ID)] as? SdkEntity
                } else {
                    this.entityLinks[EntityLink(true, SDK_CONNECTION_ID)] as? SdkEntity
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(false, SDK_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToOneChildOfParent(SDK_CONNECTION_ID, this, value)
                }
                else {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(false, SDK_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this.entityLinks[EntityLink(true, SDK_CONNECTION_ID)] = value
                }
                changedProperty.add("sdk")
            }
        
        override var libraryProperties: LibraryPropertiesEntity?
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToOneChild(LIBRARYPROPERTIES_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true, LIBRARYPROPERTIES_CONNECTION_ID)] as? LibraryPropertiesEntity
                } else {
                    this.entityLinks[EntityLink(true, LIBRARYPROPERTIES_CONNECTION_ID)] as? LibraryPropertiesEntity
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(false, LIBRARYPROPERTIES_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToOneChildOfParent(LIBRARYPROPERTIES_CONNECTION_ID, this, value)
                }
                else {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(false, LIBRARYPROPERTIES_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this.entityLinks[EntityLink(true, LIBRARYPROPERTIES_CONNECTION_ID)] = value
                }
                changedProperty.add("libraryProperties")
            }
        
        override var libraryFilesPackagingElement: LibraryFilesPackagingElementEntity?
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToOneChild(LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true, LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID)] as? LibraryFilesPackagingElementEntity
                } else {
                    this.entityLinks[EntityLink(true, LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID)] as? LibraryFilesPackagingElementEntity
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(false, LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToOneChildOfParent(LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID, this, value)
                }
                else {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(false, LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this.entityLinks[EntityLink(true, LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID)] = value
                }
                changedProperty.add("libraryFilesPackagingElement")
            }
        
        override fun getEntityData(): LibraryEntityData = result ?: super.getEntityData() as LibraryEntityData
        override fun getEntityClass(): Class<LibraryEntity> = LibraryEntity::class.java
    }
}
    
class LibraryEntityData : WorkspaceEntityData.WithCalculablePersistentId<LibraryEntity>(), SoftLinkable {
    lateinit var name: String
    lateinit var tableId: LibraryTableId
    lateinit var roots: List<LibraryRoot>
    lateinit var excludedRoots: List<VirtualFileUrl>

    fun isNameInitialized(): Boolean = ::name.isInitialized
    fun isTableIdInitialized(): Boolean = ::tableId.isInitialized
    fun isRootsInitialized(): Boolean = ::roots.isInitialized
    fun isExcludedRootsInitialized(): Boolean = ::excludedRoots.isInitialized

    override fun getLinks(): Set<PersistentEntityId<*>> {
        val result = HashSet<PersistentEntityId<*>>()
        val _tableId = tableId
        when (_tableId) {
            is LibraryTableId.ModuleLibraryTableId ->  {
                result.add(_tableId.moduleId)
            }
            is LibraryTableId.ProjectLibraryTableId ->  {
            }
            is LibraryTableId.GlobalLibraryTableId ->  {
            }
        }
        for (item in roots) {
        }
        for (item in excludedRoots) {
        }
        return result
    }

    override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
        val _tableId = tableId
        when (_tableId) {
            is LibraryTableId.ModuleLibraryTableId ->  {
                index.index(this, _tableId.moduleId)
            }
            is LibraryTableId.ProjectLibraryTableId ->  {
            }
            is LibraryTableId.GlobalLibraryTableId ->  {
            }
        }
        for (item in roots) {
        }
        for (item in excludedRoots) {
        }
    }

    override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
        // TODO verify logic
        val mutablePreviousSet = HashSet(prev)
        val _tableId = tableId
        when (_tableId) {
            is LibraryTableId.ModuleLibraryTableId ->  {
                val removedItem__tableId_moduleId = mutablePreviousSet.remove(_tableId.moduleId)
                if (!removedItem__tableId_moduleId) {
                    index.index(this, _tableId.moduleId)
                }
            }
            is LibraryTableId.ProjectLibraryTableId ->  {
            }
            is LibraryTableId.GlobalLibraryTableId ->  {
            }
        }
        for (item in roots) {
        }
        for (item in excludedRoots) {
        }
        for (removed in mutablePreviousSet) {
            index.remove(this, removed)
        }
    }

    override fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean {
        var changed = false
        val _tableId = tableId
        val res_tableId =         when (_tableId) {
            is LibraryTableId.ModuleLibraryTableId ->  {
                val _tableId_moduleId_data =                 if (_tableId.moduleId == oldLink) {
                    changed = true
                    newLink as ModuleId
                }
                else {
                    null
                }
                var _tableId_data = _tableId
                if (_tableId_moduleId_data != null) {
                    _tableId_data = _tableId_data.copy(moduleId = _tableId_moduleId_data)
                }
                _tableId_data
            }
            is LibraryTableId.ProjectLibraryTableId ->  {
                _tableId
            }
            is LibraryTableId.GlobalLibraryTableId ->  {
                _tableId
            }
        }
        if (res_tableId != null) {
            tableId = res_tableId
        }
        return changed
    }

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<LibraryEntity> {
        val modifiable = LibraryEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): LibraryEntity {
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

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return LibraryEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
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