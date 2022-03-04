package com.intellij.workspace.model.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyParentOfChild
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class FileOrDirectoryPackagingElementEntityImpl: FileOrDirectoryPackagingElementEntity, WorkspaceEntityBase() {
    
    companion object {
        private val COMPOSITEPACKAGINGELEMENT_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java, PackagingElementEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
    }
    
    override val factory: ObjType<*, *>
        get() = FileOrDirectoryPackagingElementEntity
        
    override val compositePackagingElement: CompositePackagingElementEntity
        get() = snapshot.extractOneToAbstractManyParent(COMPOSITEPACKAGINGELEMENT_CONNECTION_ID, this)!!           
        
    @JvmField var _filePath: String? = null
    override val filePath: String
        get() = _filePath!!

    class Builder(val result: FileOrDirectoryPackagingElementEntityData?): ModifiableWorkspaceEntityBase<FileOrDirectoryPackagingElementEntity>(), FileOrDirectoryPackagingElementEntity.Builder {
        constructor(): this(FileOrDirectoryPackagingElementEntityData())
                 
        override val factory: ObjType<FileOrDirectoryPackagingElementEntity, *> get() = TODO()
        override fun build(): FileOrDirectoryPackagingElementEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity FileOrDirectoryPackagingElementEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            
            // Adding parents and references to the parent
            val __compositePackagingElement = _compositePackagingElement
            if (__compositePackagingElement != null && (__compositePackagingElement is ModifiableWorkspaceEntityBase<*>) && __compositePackagingElement.diff == null) {
                builder.addEntity(__compositePackagingElement)
            }
            if (__compositePackagingElement != null && (__compositePackagingElement is ModifiableWorkspaceEntityBase<*>) && __compositePackagingElement.diff != null) {
                val access = __compositePackagingElement::class.memberProperties.single { it.name == "_children" } as KMutableProperty1<*, *>
                val __mutChildren = (access.getter.call(__compositePackagingElement) as? List<*>)?.toMutableList()
                __mutChildren?.remove(this)
                access.setter.call(__compositePackagingElement, if (__mutChildren.isNullOrEmpty()) null else __mutChildren)
            }
            if (__compositePackagingElement != null) {
                applyParentRef(COMPOSITEPACKAGINGELEMENT_CONNECTION_ID, __compositePackagingElement)
                this._compositePackagingElement = null
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
            if (!getEntityData().isFilePathInitialized()) {
                error("Field FileOrDirectoryPackagingElementEntity#filePath should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field FileOrDirectoryPackagingElementEntity#entitySource should be initialized")
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
                    val _diff = diff
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
        
        override var filePath: String
            get() = getEntityData().filePath
            set(value) {
                getEntityData().filePath = value
                changedProperty.add("filePath")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
        
        override fun hasNewValue(field: Field<in FileOrDirectoryPackagingElementEntity, *>): Boolean = TODO("Not yet implemented")                                                                     
        override fun <V> setValue(field: Field<in FileOrDirectoryPackagingElementEntity, V>, value: V) = TODO("Not yet implemented")
        override fun getEntityData(): FileOrDirectoryPackagingElementEntityData = result ?: super.getEntityData() as FileOrDirectoryPackagingElementEntityData
        override fun getEntityClass(): Class<FileOrDirectoryPackagingElementEntity> = FileOrDirectoryPackagingElementEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(FileOrDirectoryPackagingElementEntityData())
}
    
class FileOrDirectoryPackagingElementEntityData : WorkspaceEntityData<FileOrDirectoryPackagingElementEntity>() {
    lateinit var filePath: String

    fun isFilePathInitialized(): Boolean = ::filePath.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<FileOrDirectoryPackagingElementEntity> {
        val modifiable = FileOrDirectoryPackagingElementEntityImpl.Builder(null)
        modifiable.diff = diff
        modifiable.snapshot = diff
        modifiable.id = createEntityId()
        modifiable.entitySource = this.entitySource
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): FileOrDirectoryPackagingElementEntity {
        val entity = FileOrDirectoryPackagingElementEntityImpl()
        entity._filePath = filePath
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as FileOrDirectoryPackagingElementEntityData
        
        if (this.filePath != other.filePath) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as FileOrDirectoryPackagingElementEntityData
        
        if (this.filePath != other.filePath) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + filePath.hashCode()
        return result
    }
}