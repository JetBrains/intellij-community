package com.intellij.workspace.model.api

import com.intellij.workspaceModel.codegen.storage.url.VirtualFileUrl
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
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class SdkEntityImpl: SdkEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val LIBRARY_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java, SdkEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
    
    override val factory: ObjType<*, *>
        get() = SdkEntity
        
    override val library: LibraryEntity
        get() = snapshot.extractOneToOneParent(LIBRARY_CONNECTION_ID, this)!!           
        
    @JvmField var _homeUrl: VirtualFileUrl? = null
    override val homeUrl: VirtualFileUrl
        get() = _homeUrl!!

    class Builder(val result: SdkEntityData?): ModifiableWorkspaceEntityBase<SdkEntity>(), SdkEntity.Builder {
        constructor(): this(SdkEntityData())
                 
        override val factory: ObjType<SdkEntity, *> get() = TODO()
        override fun build(): SdkEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity SdkEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            index(this, "homeUrl", this.homeUrl)
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
                (__library as LibraryEntityImpl.Builder)._sdk = null
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
                    error("Field SdkEntity#library should be initialized")
                }
            }
            else {
                if (_library == null) {
                    error("Field SdkEntity#library should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field SdkEntity#entitySource should be initialized")
            }
            if (!getEntityData().isHomeUrlInitialized()) {
                error("Field SdkEntity#homeUrl should be initialized")
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
                            value._sdk = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(LIBRARY_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is LibraryEntityImpl.Builder) {
                            value._sdk = this
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
            
        override var homeUrl: VirtualFileUrl
            get() = getEntityData().homeUrl
            set(value) {
                checkModificationAllowed()
                getEntityData().homeUrl = value
                changedProperty.add("homeUrl")
                val _diff = diff
                if (_diff != null) index(this, "homeUrl", value)
            }
        
        override fun getEntityData(): SdkEntityData = result ?: super.getEntityData() as SdkEntityData
        override fun getEntityClass(): Class<SdkEntity> = SdkEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(SdkEntityData())
}
    
class SdkEntityData : WorkspaceEntityData<SdkEntity>() {
    lateinit var homeUrl: VirtualFileUrl

    fun isHomeUrlInitialized(): Boolean = ::homeUrl.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<SdkEntity> {
        val modifiable = SdkEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): SdkEntity {
        val entity = SdkEntityImpl()
        entity._homeUrl = homeUrl
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SdkEntityData
        
        if (this.entitySource != other.entitySource) return false
        if (this.homeUrl != other.homeUrl) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SdkEntityData
        
        if (this.homeUrl != other.homeUrl) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + homeUrl.hashCode()
        return result
    }
}