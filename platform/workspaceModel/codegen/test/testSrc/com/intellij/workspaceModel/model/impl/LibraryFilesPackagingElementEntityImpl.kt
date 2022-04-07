package com.intellij.workspace.model.api

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
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyParentOfChild
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class LibraryFilesPackagingElementEntityImpl: LibraryFilesPackagingElementEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val COMPOSITEPACKAGINGELEMENT_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java, PackagingElementEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
        internal val LIBRARY_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java, LibraryFilesPackagingElementEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, true)
    }
    
    override val factory: ObjType<*, *>
        get() = LibraryFilesPackagingElementEntity
        
    override val compositePackagingElement: CompositePackagingElementEntity
        get() = snapshot.extractOneToAbstractManyParent(COMPOSITEPACKAGINGELEMENT_CONNECTION_ID, this)!!           
        
    override val library: LibraryEntity?
        get() = snapshot.extractOneToOneParent(LIBRARY_CONNECTION_ID, this)

    class Builder(val result: LibraryFilesPackagingElementEntityData?): ModifiableWorkspaceEntityBase<LibraryFilesPackagingElementEntity>(), LibraryFilesPackagingElementEntity.Builder {
        constructor(): this(LibraryFilesPackagingElementEntityData())
                 
        override val factory: ObjType<LibraryFilesPackagingElementEntity, *> get() = TODO()
        override fun build(): LibraryFilesPackagingElementEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity LibraryFilesPackagingElementEntity is already created in a different builder")
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
            val __compositePackagingElement = _compositePackagingElement
            if (__compositePackagingElement != null && (__compositePackagingElement is ModifiableWorkspaceEntityBase<*>) && __compositePackagingElement.diff == null) {
                builder.addEntity(__compositePackagingElement)
            }
            if (__compositePackagingElement != null && (__compositePackagingElement is ModifiableWorkspaceEntityBase<*>) && __compositePackagingElement.diff != null) {
                // Set field to null (in referenced entity)
                val access = __compositePackagingElement::class.memberProperties.single { it.name == "_children" } as KMutableProperty1<*, *>
                val __mutChildren = (access.getter.call(__compositePackagingElement) as? List<*>)?.toMutableList()
                __mutChildren?.remove(this)
                access.setter.call(__compositePackagingElement, if (__mutChildren.isNullOrEmpty()) null else __mutChildren)
            }
            if (__compositePackagingElement != null) {
                applyParentRef(COMPOSITEPACKAGINGELEMENT_CONNECTION_ID, __compositePackagingElement)
                this._compositePackagingElement = null
            }
            val __library = _library
            if (__library != null && (__library is ModifiableWorkspaceEntityBase<*>) && __library.diff == null) {
                builder.addEntity(__library)
            }
            if (__library != null && (__library is ModifiableWorkspaceEntityBase<*>) && __library.diff != null) {
                // Set field to null (in referenced entity)
                (__library as LibraryEntityImpl.Builder)._libraryFilesPackagingElement = null
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
                if (_diff.extractOneToAbstractManyParent<WorkspaceEntityBase>(COMPOSITEPACKAGINGELEMENT_CONNECTION_ID, this) == null) {
                    error("Field PackagingElementEntity#compositePackagingElement should be initialized")
                }
            }
            else {
                if (_compositePackagingElement == null) {
                    error("Field PackagingElementEntity#compositePackagingElement should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field LibraryFilesPackagingElementEntity#entitySource should be initialized")
            }
        }
    
        
            var _compositePackagingElement: CompositePackagingElementEntity? = null
            override var compositePackagingElement: CompositePackagingElementEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToAbstractManyParent(COMPOSITEPACKAGINGELEMENT_CONNECTION_ID, this) ?: _compositePackagingElement!!
                    } else {
                        _compositePackagingElement!!
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
                        _diff.updateOneToAbstractManyParentOfChild(COMPOSITEPACKAGINGELEMENT_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value != null) {
                            val access = value::class.memberProperties.single { it.name == "_children" } as KMutableProperty1<*, *>
                            access.setter.call(value, ((access.getter.call(value) as? List<*>) ?: emptyList<Any>()) + this)
                        }
                        
                        this._compositePackagingElement = value
                    }
                    changedProperty.add("compositePackagingElement")
                }
        
            var _library: LibraryEntity? = null
            override var library: LibraryEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneParent(LIBRARY_CONNECTION_ID, this) ?: _library
                    } else {
                        _library
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is LibraryEntityImpl.Builder) {
                            value._libraryFilesPackagingElement = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(LIBRARY_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is LibraryEntityImpl.Builder) {
                            value._libraryFilesPackagingElement = this
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
        
        override fun getEntityData(): LibraryFilesPackagingElementEntityData = result ?: super.getEntityData() as LibraryFilesPackagingElementEntityData
        override fun getEntityClass(): Class<LibraryFilesPackagingElementEntity> = LibraryFilesPackagingElementEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(LibraryFilesPackagingElementEntityData())
}
    
class LibraryFilesPackagingElementEntityData : WorkspaceEntityData<LibraryFilesPackagingElementEntity>() {


    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<LibraryFilesPackagingElementEntity> {
        val modifiable = LibraryFilesPackagingElementEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): LibraryFilesPackagingElementEntity {
        val entity = LibraryFilesPackagingElementEntityImpl()
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as LibraryFilesPackagingElementEntityData
        
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as LibraryFilesPackagingElementEntityData
        
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        return result
    }
}