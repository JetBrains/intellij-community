// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyParentOfChild
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import org.jetbrains.deft.ObjBuilder

    

open class ExtractedDirectoryPackagingElementEntityImpl: ExtractedDirectoryPackagingElementEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java, PackagingElementEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, true)
    }
        
    override val parentEntity: CompositePackagingElementEntity?
        get() = snapshot.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this)           
        
    @JvmField var _filePath: VirtualFileUrl? = null
    override val filePath: VirtualFileUrl
        get() = _filePath!!
                        
    @JvmField var _pathInArchive: String? = null
    override val pathInArchive: String
        get() = _pathInArchive!!

    class Builder(val result: ExtractedDirectoryPackagingElementEntityData?): ModifiableWorkspaceEntityBase<ExtractedDirectoryPackagingElementEntity>(), ExtractedDirectoryPackagingElementEntity.Builder {
        constructor(): this(ExtractedDirectoryPackagingElementEntityData())
                 
        override fun build(): ExtractedDirectoryPackagingElementEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ExtractedDirectoryPackagingElementEntity is already created in a different builder")
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
            val __parentEntity = _parentEntity
            if (__parentEntity != null && (__parentEntity is ModifiableWorkspaceEntityBase<*>) && __parentEntity.diff == null) {
                builder.addEntity(__parentEntity)
            }
            if (__parentEntity != null && (__parentEntity is ModifiableWorkspaceEntityBase<*>) && __parentEntity.diff != null) {
                // Set field to null (in referenced entity)
                val access = __parentEntity::class.memberProperties.single { it.name == "_children" } as KMutableProperty1<*, *>
                val __mutChildren = (access.getter.call(__parentEntity) as? List<*>)?.toMutableList()
                __mutChildren?.remove(this)
                access.setter.call(__parentEntity, if (__mutChildren.isNullOrEmpty()) null else __mutChildren)
            }
            if (__parentEntity != null) {
                applyParentRef(PARENTENTITY_CONNECTION_ID, __parentEntity)
                this._parentEntity = null
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
            if (!getEntityData().isFilePathInitialized()) {
                error("Field FileOrDirectoryPackagingElementEntity#filePath should be initialized")
            }
            if (!getEntityData().isPathInArchiveInitialized()) {
                error("Field ExtractedDirectoryPackagingElementEntity#pathInArchive should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ExtractedDirectoryPackagingElementEntity#entitySource should be initialized")
            }
        }
    
        
            var _parentEntity: CompositePackagingElementEntity? = null
            override var parentEntity: CompositePackagingElementEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this) ?: _parentEntity
                    } else {
                        _parentEntity
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value != null) {
                            val access = value::class.memberProperties.single { it.name == "_children" } as KMutableProperty1<*, *>
                            access.setter.call(value, ((access.getter.call(value) as? List<*>) ?: emptyList<Any>()) + this)
                        }
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToAbstractManyParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value != null) {
                            val access = value::class.memberProperties.single { it.name == "_children" } as KMutableProperty1<*, *>
                            access.setter.call(value, ((access.getter.call(value) as? List<*>) ?: emptyList<Any>()) + this)
                        }
                        
                        this._parentEntity = value
                    }
                    changedProperty.add("parentEntity")
                }
        
        override var filePath: VirtualFileUrl
            get() = getEntityData().filePath
            set(value) {
                checkModificationAllowed()
                getEntityData().filePath = value
                changedProperty.add("filePath")
                val _diff = diff
                if (_diff != null) index(this, "filePath", value)
            }
            
        override var pathInArchive: String
            get() = getEntityData().pathInArchive
            set(value) {
                checkModificationAllowed()
                getEntityData().pathInArchive = value
                changedProperty.add("pathInArchive")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
        
        override fun getEntityData(): ExtractedDirectoryPackagingElementEntityData = result ?: super.getEntityData() as ExtractedDirectoryPackagingElementEntityData
        override fun getEntityClass(): Class<ExtractedDirectoryPackagingElementEntity> = ExtractedDirectoryPackagingElementEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(ExtractedDirectoryPackagingElementEntityData())
}
    
class ExtractedDirectoryPackagingElementEntityData : WorkspaceEntityData<ExtractedDirectoryPackagingElementEntity>() {
    lateinit var filePath: VirtualFileUrl
    lateinit var pathInArchive: String

    fun isFilePathInitialized(): Boolean = ::filePath.isInitialized
    fun isPathInArchiveInitialized(): Boolean = ::pathInArchive.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<ExtractedDirectoryPackagingElementEntity> {
        val modifiable = ExtractedDirectoryPackagingElementEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): ExtractedDirectoryPackagingElementEntity {
        val entity = ExtractedDirectoryPackagingElementEntityImpl()
        entity._filePath = filePath
        entity._pathInArchive = pathInArchive
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ExtractedDirectoryPackagingElementEntityData
        
        if (this.filePath != other.filePath) return false
        if (this.pathInArchive != other.pathInArchive) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ExtractedDirectoryPackagingElementEntityData
        
        if (this.filePath != other.filePath) return false
        if (this.pathInArchive != other.pathInArchive) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + filePath.hashCode()
        result = 31 * result + pathInArchive.hashCode()
        return result
    }
}