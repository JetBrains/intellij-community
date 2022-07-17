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
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractOneChild
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractOneChildOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ArtifactEntityImpl: ArtifactEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val ROOTELEMENT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java, CompositePackagingElementEntity::class.java, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, true)
        internal val CUSTOMPROPERTIES_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java, ArtifactPropertiesEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        internal val ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java, ArtifactOutputPackagingElementEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        
        val connections = listOf<ConnectionId>(
            ROOTELEMENT_CONNECTION_ID,
            CUSTOMPROPERTIES_CONNECTION_ID,
            ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID,
        )

    }
        
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
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: ArtifactEntityData?): ModifiableWorkspaceEntityBase<ArtifactEntity>(), ArtifactEntity.Builder {
        constructor(): this(ArtifactEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
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
            // Process linked entities that are connected without a builder
            processLinkedEntities(builder)
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
                if (this.entityLinks[EntityLink(true, ROOTELEMENT_CONNECTION_ID)] == null) {
                    error("Field ArtifactEntity#rootElement should be initialized")
                }
            }
            // Check initialization for list with ref type
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CUSTOMPROPERTIES_CONNECTION_ID, this) == null) {
                    error("Field ArtifactEntity#customProperties should be initialized")
                }
            }
            else {
                if (this.entityLinks[EntityLink(true, CUSTOMPROPERTIES_CONNECTION_ID)] == null) {
                    error("Field ArtifactEntity#customProperties should be initialized")
                }
            }
        }
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
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
            
        override var rootElement: CompositePackagingElementEntity
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToAbstractOneChild(ROOTELEMENT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true, ROOTELEMENT_CONNECTION_ID)]!! as CompositePackagingElementEntity
                } else {
                    this.entityLinks[EntityLink(true, ROOTELEMENT_CONNECTION_ID)]!! as CompositePackagingElementEntity
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(false, ROOTELEMENT_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToAbstractOneChildOfParent(ROOTELEMENT_CONNECTION_ID, this, value)
                }
                else {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(false, ROOTELEMENT_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this.entityLinks[EntityLink(true, ROOTELEMENT_CONNECTION_ID)] = value
                }
                changedProperty.add("rootElement")
            }
        
        // List of non-abstract referenced types
        var _customProperties: List<ArtifactPropertiesEntity>? = emptyList()
        override var customProperties: List<ArtifactPropertiesEntity>
            get() {
                // Getter of the list of non-abstract referenced types
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToManyChildren<ArtifactPropertiesEntity>(CUSTOMPROPERTIES_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(true, CUSTOMPROPERTIES_CONNECTION_ID)] as? List<ArtifactPropertiesEntity> ?: emptyList())
                } else {
                    this.entityLinks[EntityLink(true, CUSTOMPROPERTIES_CONNECTION_ID)] as? List<ArtifactPropertiesEntity> ?: emptyList()
                }
            }
            set(value) {
                // Setter of the list of non-abstract referenced types
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null) {
                    for (item_value in value) {
                        if (item_value is ModifiableWorkspaceEntityBase<*> && (item_value as? ModifiableWorkspaceEntityBase<*>)?.diff == null) {
                            _diff.addEntity(item_value)
                        }
                    }
                    _diff.updateOneToManyChildrenOfParent(CUSTOMPROPERTIES_CONNECTION_ID, this, value)
                }
                else {
                    for (item_value in value) {
                        if (item_value is ModifiableWorkspaceEntityBase<*>) {
                            item_value.entityLinks[EntityLink(false, CUSTOMPROPERTIES_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                    }
                    
                    this.entityLinks[EntityLink(true, CUSTOMPROPERTIES_CONNECTION_ID)] = value
                }
                changedProperty.add("customProperties")
            }
        
        override var artifactOutputPackagingElement: ArtifactOutputPackagingElementEntity?
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToOneChild(ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true, ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID)] as? ArtifactOutputPackagingElementEntity
                } else {
                    this.entityLinks[EntityLink(true, ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID)] as? ArtifactOutputPackagingElementEntity
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(false, ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToOneChildOfParent(ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID, this, value)
                }
                else {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(false, ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this.entityLinks[EntityLink(true, ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID)] = value
                }
                changedProperty.add("artifactOutputPackagingElement")
            }
        
        override fun getEntityData(): ArtifactEntityData = result ?: super.getEntityData() as ArtifactEntityData
        override fun getEntityClass(): Class<ArtifactEntity> = ArtifactEntity::class.java
    }
}
    
class ArtifactEntityData : WorkspaceEntityData.WithCalculablePersistentId<ArtifactEntity>() {
    lateinit var name: String
    lateinit var artifactType: String
    var includeInProjectBuild: Boolean = false
    var outputUrl: VirtualFileUrl? = null

    fun isNameInitialized(): Boolean = ::name.isInitialized
    fun isArtifactTypeInitialized(): Boolean = ::artifactType.isInitialized
    

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ArtifactEntity> {
        val modifiable = ArtifactEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ArtifactEntity {
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

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ArtifactEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
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