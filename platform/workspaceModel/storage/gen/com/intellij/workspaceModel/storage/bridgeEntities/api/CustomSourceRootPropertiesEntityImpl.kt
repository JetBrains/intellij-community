package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class CustomSourceRootPropertiesEntityImpl: CustomSourceRootPropertiesEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val SOURCEROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java, CustomSourceRootPropertiesEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        
        val connections = listOf<ConnectionId>(
            SOURCEROOT_CONNECTION_ID,
        )

    }
        
    override val sourceRoot: SourceRootEntity
        get() = snapshot.extractOneToOneParent(SOURCEROOT_CONNECTION_ID, this)!!           
        
    @JvmField var _propertiesXmlTag: String? = null
    override val propertiesXmlTag: String
        get() = _propertiesXmlTag!!
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: CustomSourceRootPropertiesEntityData?): ModifiableWorkspaceEntityBase<CustomSourceRootPropertiesEntity>(), CustomSourceRootPropertiesEntity.Builder {
        constructor(): this(CustomSourceRootPropertiesEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity CustomSourceRootPropertiesEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            // Process linked entities that are connected without a builder
            processLinkedEntities(builder)
            checkInitialization() // TODO uncomment and check failed tests
        }
    
        fun checkInitialization() {
            val _diff = diff
            if (_diff != null) {
                if (_diff.extractOneToOneParent<WorkspaceEntityBase>(SOURCEROOT_CONNECTION_ID, this) == null) {
                    error("Field CustomSourceRootPropertiesEntity#sourceRoot should be initialized")
                }
            }
            else {
                if (this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)] == null) {
                    error("Field CustomSourceRootPropertiesEntity#sourceRoot should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field CustomSourceRootPropertiesEntity#entitySource should be initialized")
            }
            if (!getEntityData().isPropertiesXmlTagInitialized()) {
                error("Field CustomSourceRootPropertiesEntity#propertiesXmlTag should be initialized")
            }
        }
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }
    
        
        override var sourceRoot: SourceRootEntity
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToOneParent(SOURCEROOT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)]!! as SourceRootEntity
                } else {
                    this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)]!! as SourceRootEntity
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToOneParentOfChild(SOURCEROOT_CONNECTION_ID, this, value)
                }
                else {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)] = value
                }
                changedProperty.add("sourceRoot")
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
        
        override fun getEntityData(): CustomSourceRootPropertiesEntityData = result ?: super.getEntityData() as CustomSourceRootPropertiesEntityData
        override fun getEntityClass(): Class<CustomSourceRootPropertiesEntity> = CustomSourceRootPropertiesEntity::class.java
    }
}
    
class CustomSourceRootPropertiesEntityData : WorkspaceEntityData<CustomSourceRootPropertiesEntity>() {
    lateinit var propertiesXmlTag: String

    fun isPropertiesXmlTagInitialized(): Boolean = ::propertiesXmlTag.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<CustomSourceRootPropertiesEntity> {
        val modifiable = CustomSourceRootPropertiesEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): CustomSourceRootPropertiesEntity {
        val entity = CustomSourceRootPropertiesEntityImpl()
        entity._propertiesXmlTag = propertiesXmlTag
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return CustomSourceRootPropertiesEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as CustomSourceRootPropertiesEntityData
        
        if (this.entitySource != other.entitySource) return false
        if (this.propertiesXmlTag != other.propertiesXmlTag) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as CustomSourceRootPropertiesEntityData
        
        if (this.propertiesXmlTag != other.propertiesXmlTag) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + propertiesXmlTag.hashCode()
        return result
    }
}