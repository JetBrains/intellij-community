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
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class CustomPackagingElementEntityImpl: CustomPackagingElementEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val COMPOSITEPACKAGINGELEMENT_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java, PackagingElementEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
        internal val ARTIFACT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java, CompositePackagingElementEntity::class.java, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false)
        internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java, PackagingElementEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
    }
        
    override val compositePackagingElement: CompositePackagingElementEntity
        get() = snapshot.extractOneToAbstractManyParent(COMPOSITEPACKAGINGELEMENT_CONNECTION_ID, this)!!           
        
    override val artifact: ArtifactEntity
        get() = snapshot.extractOneToAbstractOneParent(ARTIFACT_CONNECTION_ID, this)!!           
        
    override val children: List<PackagingElementEntity>
        get() = snapshot.extractOneToAbstractManyChildren<PackagingElementEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()
    
    @JvmField var _typeId: String? = null
    override val typeId: String
        get() = _typeId!!
                        
    @JvmField var _propertiesXmlTag: String? = null
    override val propertiesXmlTag: String
        get() = _propertiesXmlTag!!

    class Builder(val result: CustomPackagingElementEntityData?): ModifiableWorkspaceEntityBase<CustomPackagingElementEntity>(), CustomPackagingElementEntity.Builder {
        constructor(): this(CustomPackagingElementEntityData())
                 
        override fun build(): CustomPackagingElementEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity CustomPackagingElementEntity is already created in a different builder")
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
            val __artifact = _artifact
            if (__artifact != null && (__artifact is ModifiableWorkspaceEntityBase<*>) && __artifact.diff == null) {
                builder.addEntity(__artifact)
            }
            if (__artifact != null && (__artifact is ModifiableWorkspaceEntityBase<*>) && __artifact.diff != null) {
                // Set field to null (in referenced entity)
                (__artifact as ArtifactEntityImpl.Builder)._rootElement = null
            }
            if (__artifact != null) {
                applyParentRef(ARTIFACT_CONNECTION_ID, __artifact)
                this._artifact = null
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
            if (!getEntityData().isTypeIdInitialized()) {
                error("Field CustomPackagingElementEntity#typeId should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field CustomPackagingElementEntity#entitySource should be initialized")
            }
            if (!getEntityData().isPropertiesXmlTagInitialized()) {
                error("Field CustomPackagingElementEntity#propertiesXmlTag should be initialized")
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
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ArtifactEntityImpl.Builder) {
                            value._rootElement = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
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
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null) {
                        for (item_value in value) {
                            if ((item_value as? ModifiableWorkspaceEntityBase<*>)?.diff == null) {
                                _diff.addEntity(item_value)
                            }
                        }
                        _diff.updateOneToAbstractManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value.asSequence())
                    }
                    else {
                        for (item_value in value) {
                            if (item_value != null) {
                                val access = item_value::class.memberProperties.single { it.name == "_compositePackagingElement" } as KMutableProperty1<*, *>
                                access.setter.call(item_value, this)
                            }
                        }
                        
                        _children = value
                    }
                    changedProperty.add("children")
                }
        
        override var typeId: String
            get() = getEntityData().typeId
            set(value) {
                checkModificationAllowed()
                getEntityData().typeId = value
                changedProperty.add("typeId")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var propertiesXmlTag: String
            get() = getEntityData().propertiesXmlTag
            set(value) {
                checkModificationAllowed()
                getEntityData().propertiesXmlTag = value
                changedProperty.add("propertiesXmlTag")
            }
        
        override fun getEntityData(): CustomPackagingElementEntityData = result ?: super.getEntityData() as CustomPackagingElementEntityData
        override fun getEntityClass(): Class<CustomPackagingElementEntity> = CustomPackagingElementEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(CustomPackagingElementEntityData())
}
    
class CustomPackagingElementEntityData : WorkspaceEntityData<CustomPackagingElementEntity>() {
    lateinit var typeId: String
    lateinit var propertiesXmlTag: String

    fun isTypeIdInitialized(): Boolean = ::typeId.isInitialized
    fun isPropertiesXmlTagInitialized(): Boolean = ::propertiesXmlTag.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<CustomPackagingElementEntity> {
        val modifiable = CustomPackagingElementEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): CustomPackagingElementEntity {
        val entity = CustomPackagingElementEntityImpl()
        entity._typeId = typeId
        entity._propertiesXmlTag = propertiesXmlTag
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as CustomPackagingElementEntityData
        
        if (this.typeId != other.typeId) return false
        if (this.entitySource != other.entitySource) return false
        if (this.propertiesXmlTag != other.propertiesXmlTag) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as CustomPackagingElementEntityData
        
        if (this.typeId != other.typeId) return false
        if (this.propertiesXmlTag != other.propertiesXmlTag) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + typeId.hashCode()
        result = 31 * result + propertiesXmlTag.hashCode()
        return result
    }
}