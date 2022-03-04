package com.intellij.workspace.model.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyChildren
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyParent
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractOneParent
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyParentOfChild
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractOneParentOfChild
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class DirectoryPackagingElementEntityImpl: DirectoryPackagingElementEntity, WorkspaceEntityBase() {
    
    companion object {
        private val COMPOSITEPACKAGINGELEMENT_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java, PackagingElementEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
        private val ARTIFACT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java, CompositePackagingElementEntity::class.java, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false)
        private val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java, PackagingElementEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
    }
    
    override val factory: ObjType<*, *>
        get() = DirectoryPackagingElementEntity
        
    override val compositePackagingElement: CompositePackagingElementEntity
        get() = snapshot.extractOneToAbstractManyParent(COMPOSITEPACKAGINGELEMENT_CONNECTION_ID, this)!!           
        
    override val artifact: ArtifactEntity
        get() = snapshot.extractOneToAbstractOneParent(ARTIFACT_CONNECTION_ID, this)!!           
        
    override val children: List<PackagingElementEntity>
        get() = snapshot.extractOneToAbstractManyChildren<PackagingElementEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()
    
    @JvmField var _directoryName: String? = null
    override val directoryName: String
        get() = _directoryName!!

    class Builder(val result: DirectoryPackagingElementEntityData?): ModifiableWorkspaceEntityBase<DirectoryPackagingElementEntity>(), DirectoryPackagingElementEntity.Builder {
        constructor(): this(DirectoryPackagingElementEntityData())
                 
        override val factory: ObjType<DirectoryPackagingElementEntity, *> get() = TODO()
        override fun build(): DirectoryPackagingElementEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity DirectoryPackagingElementEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            val __children = _children!!
            for (item in __children) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_children, woBuilder_children) = __children.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(CHILDREN_CONNECTION_ID, withBuilder_children)
            this._children = if (woBuilder_children.isNotEmpty()) woBuilder_children else null
            
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
            val __artifact = _artifact
            if (__artifact != null && (__artifact is ModifiableWorkspaceEntityBase<*>) && __artifact.diff == null) {
                builder.addEntity(__artifact)
            }
            if (__artifact != null && (__artifact is ModifiableWorkspaceEntityBase<*>) && __artifact.diff != null) {
                (__artifact as ArtifactEntityImpl.Builder)._rootElement = null
            }
            if (__artifact != null) {
                applyParentRef(ARTIFACT_CONNECTION_ID, __artifact)
                this._artifact = null
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
            if (_diff != null) {
                if (_diff.extractOneToAbstractOneParent<WorkspaceEntityBase>(ARTIFACT_CONNECTION_ID, this) == null) {
                    error("Field CompositePackagingElementEntity#artifact should be initialized")
                }
            }
            else {
                if (_artifact == null) {
                    error("Field CompositePackagingElementEntity#artifact should be initialized")
                }
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
                    error("Field CompositePackagingElementEntity#children should be initialized")
                }
            }
            else {
                if (_children == null) {
                    error("Field CompositePackagingElementEntity#children should be initialized")
                }
            }
            if (!getEntityData().isDirectoryNameInitialized()) {
                error("Field DirectoryPackagingElementEntity#directoryName should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field DirectoryPackagingElementEntity#entitySource should be initialized")
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
        
            var _artifact: ArtifactEntity? = null
            override var artifact: ArtifactEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToAbstractOneParent(ARTIFACT_CONNECTION_ID, this) ?: _artifact!!
                    } else {
                        _artifact!!
                    }
                }
                set(value) {
                    val _diff = diff
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToAbstractOneParentOfChild(ARTIFACT_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ArtifactEntityImpl.Builder) {
                            value._rootElement = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._artifact = value
                    }
                    changedProperty.add("artifact")
                }
        
            var _children: List<PackagingElementEntity>? = null
            override var children: List<PackagingElementEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToAbstractManyChildren<PackagingElementEntity>(CHILDREN_CONNECTION_ID, this)!!.toList() + (_children ?: emptyList())
                    } else {
                        _children!!
                    }
                }
                set(value) {
                    val _diff = diff
                    if (_diff != null) {
                        _diff.updateOneToAbstractManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value.asSequence())
                    }
                    else {
                        for (item_value in value) {
                            if (item_value != null) {
                                val access = item_value::class.memberProperties.single { it.name == "_compositePackagingElement" } as KMutableProperty1<*, *>
                                // y
                                access.setter.call(item_value, this)
                            }
                        }
                        
                        _children = value
                    }
                    changedProperty.add("children")
                }
        
        override var directoryName: String
            get() = getEntityData().directoryName
            set(value) {
                getEntityData().directoryName = value
                changedProperty.add("directoryName")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
        
        override fun hasNewValue(field: Field<in DirectoryPackagingElementEntity, *>): Boolean = TODO("Not yet implemented")                                                                     
        override fun <V> setValue(field: Field<in DirectoryPackagingElementEntity, V>, value: V) = TODO("Not yet implemented")
        override fun getEntityData(): DirectoryPackagingElementEntityData = result ?: super.getEntityData() as DirectoryPackagingElementEntityData
        override fun getEntityClass(): Class<DirectoryPackagingElementEntity> = DirectoryPackagingElementEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(DirectoryPackagingElementEntityData())
}
    
class DirectoryPackagingElementEntityData : WorkspaceEntityData<DirectoryPackagingElementEntity>() {
    lateinit var directoryName: String

    fun isDirectoryNameInitialized(): Boolean = ::directoryName.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<DirectoryPackagingElementEntity> {
        val modifiable = DirectoryPackagingElementEntityImpl.Builder(null)
        modifiable.diff = diff
        modifiable.snapshot = diff
        modifiable.id = createEntityId()
        modifiable.entitySource = this.entitySource
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): DirectoryPackagingElementEntity {
        val entity = DirectoryPackagingElementEntityImpl()
        entity._directoryName = directoryName
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as DirectoryPackagingElementEntityData
        
        if (this.directoryName != other.directoryName) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as DirectoryPackagingElementEntityData
        
        if (this.directoryName != other.directoryName) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + directoryName.hashCode()
        return result
    }
}