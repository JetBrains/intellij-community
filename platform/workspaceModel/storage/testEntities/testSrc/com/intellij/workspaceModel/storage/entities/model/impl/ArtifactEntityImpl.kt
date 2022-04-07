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
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractOneChild
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractOneChildOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class ArtifactEntityImpl: ArtifactEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val ROOTELEMENT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java, CompositePackagingElementEntity::class.java, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false)
        internal val CUSTOMPROPERTIES_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java, ArtifactPropertiesEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        internal val ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java, ArtifactOutputPackagingElementEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, true)
    }
    
    override val factory: ObjType<*, *>
        get() = ArtifactEntity
        
    @JvmField var _name: String? = null
    override val name: String
        get() = _name!!
                        
    @JvmField var _artifactType: String? = null
    override val artifactType: String
        get() = _artifactType!!
                        
    override var includeInProjectBuild: Boolean = false
    @JvmField var _outputUrl: VirtualFileUrl? = null
    override val outputUrl: VirtualFileUrl?
        get() = _outputUrl
                        
    override val rootElement: CompositePackagingElementEntity
        get() = snapshot.extractOneToAbstractOneChild(ROOTELEMENT_CONNECTION_ID, this)!!           
        
    override val customProperties: List<ArtifactPropertiesEntity>
        get() = snapshot.extractOneToManyChildren<ArtifactPropertiesEntity>(CUSTOMPROPERTIES_CONNECTION_ID, this)!!.toList()
    
    override val artifactOutputPackagingElement: ArtifactOutputPackagingElementEntity?
        get() = snapshot.extractOneToOneChild(ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID, this)

    class Builder(val result: ArtifactEntityData?): ModifiableWorkspaceEntityBase<ArtifactEntity>(), ArtifactEntity.Builder {
        constructor(): this(ArtifactEntityData())
                 
        override val factory: ObjType<ArtifactEntity, *> get() = TODO()
        override fun build(): ArtifactEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ArtifactEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            index(this, "outputUrl", this.outputUrl)
            val __rootElement = _rootElement
            if (__rootElement != null && __rootElement is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__rootElement)
                applyRef(ROOTELEMENT_CONNECTION_ID, __rootElement)
                this._rootElement = null
            }
            val __artifactOutputPackagingElement = _artifactOutputPackagingElement
            if (__artifactOutputPackagingElement != null && __artifactOutputPackagingElement is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__artifactOutputPackagingElement)
                applyRef(ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID, __artifactOutputPackagingElement)
                this._artifactOutputPackagingElement = null
            }
            val __customProperties = _customProperties!!
            for (item in __customProperties) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_customProperties, woBuilder_customProperties) = __customProperties.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(CUSTOMPROPERTIES_CONNECTION_ID, withBuilder_customProperties)
            this._customProperties = if (woBuilder_customProperties.isNotEmpty()) woBuilder_customProperties else null
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
                error("Field ArtifactEntity#name should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ArtifactEntity#entitySource should be initialized")
            }
            if (!getEntityData().isArtifactTypeInitialized()) {
                error("Field ArtifactEntity#artifactType should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToAbstractOneChild<WorkspaceEntityBase>(ROOTELEMENT_CONNECTION_ID, this) == null) {
                    error("Field ArtifactEntity#rootElement should be initialized")
                }
            }
            else {
                if (_rootElement == null) {
                    error("Field ArtifactEntity#rootElement should be initialized")
                }
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CUSTOMPROPERTIES_CONNECTION_ID, this) == null) {
                    error("Field ArtifactEntity#customProperties should be initialized")
                }
            }
            else {
                if (_customProperties == null) {
                    error("Field ArtifactEntity#customProperties should be initialized")
                }
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
            
        override var artifactType: String
            get() = getEntityData().artifactType
            set(value) {
                checkModificationAllowed()
                getEntityData().artifactType = value
                changedProperty.add("artifactType")
            }
            
        override var includeInProjectBuild: Boolean
            get() = getEntityData().includeInProjectBuild
            set(value) {
                checkModificationAllowed()
                getEntityData().includeInProjectBuild = value
                changedProperty.add("includeInProjectBuild")
            }
            
        override var outputUrl: VirtualFileUrl?
            get() = getEntityData().outputUrl
            set(value) {
                checkModificationAllowed()
                getEntityData().outputUrl = value
                changedProperty.add("outputUrl")
                val _diff = diff
                if (_diff != null) index(this, "outputUrl", value)
            }
            
            var _rootElement: CompositePackagingElementEntity? = null
            override var rootElement: CompositePackagingElementEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToAbstractOneChild(ROOTELEMENT_CONNECTION_ID, this) ?: _rootElement!!
                    } else {
                        _rootElement!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value != null) {
                            val access = value::class.memberProperties.single { it.name == "_artifact" } as KMutableProperty1<*, *>
                            access.setter.call(value, this)
                        }
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToAbstractOneChildOfParent(ROOTELEMENT_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value != null) {
                            val access = value::class.memberProperties.single { it.name == "_artifact" } as KMutableProperty1<*, *>
                            access.setter.call(value, this)
                        }
                        
                        this._rootElement = value
                    }
                    changedProperty.add("rootElement")
                }
        
            var _customProperties: List<ArtifactPropertiesEntity>? = null
            override var customProperties: List<ArtifactPropertiesEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyChildren<ArtifactPropertiesEntity>(CUSTOMPROPERTIES_CONNECTION_ID, this)!!.toList() + (_customProperties ?: emptyList())
                    } else {
                        _customProperties!!
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
                        _diff.updateOneToManyChildrenOfParent(CUSTOMPROPERTIES_CONNECTION_ID, this, value)
                    }
                    else {
                        for (item_value in value) {
                            if (item_value is ArtifactPropertiesEntityImpl.Builder) {
                                item_value._artifact = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _customProperties = value
                        // Test
                    }
                    changedProperty.add("customProperties")
                }
        
            var _artifactOutputPackagingElement: ArtifactOutputPackagingElementEntity? = null
            override var artifactOutputPackagingElement: ArtifactOutputPackagingElementEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneChild(ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID, this) ?: _artifactOutputPackagingElement
                    } else {
                        _artifactOutputPackagingElement
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ArtifactOutputPackagingElementEntityImpl.Builder) {
                            value._artifact = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ArtifactOutputPackagingElementEntityImpl.Builder) {
                            value._artifact = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._artifactOutputPackagingElement = value
                    }
                    changedProperty.add("artifactOutputPackagingElement")
                }
        
        override fun getEntityData(): ArtifactEntityData = result ?: super.getEntityData() as ArtifactEntityData
        override fun getEntityClass(): Class<ArtifactEntity> = ArtifactEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(ArtifactEntityData())
}
    
class ArtifactEntityData : WorkspaceEntityData.WithCalculablePersistentId<ArtifactEntity>() {
    lateinit var name: String
    lateinit var artifactType: String
    var includeInProjectBuild: Boolean = false
    var outputUrl: VirtualFileUrl? = null

    fun isNameInitialized(): Boolean = ::name.isInitialized
    fun isArtifactTypeInitialized(): Boolean = ::artifactType.isInitialized
    

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<ArtifactEntity> {
        val modifiable = ArtifactEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): ArtifactEntity {
        val entity = ArtifactEntityImpl()
        entity._name = name
        entity._artifactType = artifactType
        entity.includeInProjectBuild = includeInProjectBuild
        entity._outputUrl = outputUrl
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun persistentId(): PersistentEntityId<*> {
        return ArtifactId(name)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ArtifactEntityData
        
        if (this.name != other.name) return false
        if (this.entitySource != other.entitySource) return false
        if (this.artifactType != other.artifactType) return false
        if (this.includeInProjectBuild != other.includeInProjectBuild) return false
        if (this.outputUrl != other.outputUrl) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ArtifactEntityData
        
        if (this.name != other.name) return false
        if (this.artifactType != other.artifactType) return false
        if (this.includeInProjectBuild != other.includeInProjectBuild) return false
        if (this.outputUrl != other.outputUrl) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + artifactType.hashCode()
        result = 31 * result + includeInProjectBuild.hashCode()
        result = 31 * result + outputUrl.hashCode()
        return result
    }
}