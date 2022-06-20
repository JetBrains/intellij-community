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
open class SourceRootOrderEntityImpl: SourceRootOrderEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val CONTENTROOTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ContentRootEntity::class.java, SourceRootOrderEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        
        val connections = listOf<ConnectionId>(
            CONTENTROOTENTITY_CONNECTION_ID,
        )

    }
        
    override val contentRootEntity: ContentRootEntity
        get() = snapshot.extractOneToOneParent(CONTENTROOTENTITY_CONNECTION_ID, this)!!           
        
    @JvmField var _orderOfSourceRoots: List<VirtualFileUrl>? = null
    override val orderOfSourceRoots: List<VirtualFileUrl>
        get() = _orderOfSourceRoots!!
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: SourceRootOrderEntityData?): ModifiableWorkspaceEntityBase<SourceRootOrderEntity>(), SourceRootOrderEntity.Builder {
        constructor(): this(SourceRootOrderEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity SourceRootOrderEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            index(this, "orderOfSourceRoots", this.orderOfSourceRoots.toHashSet())
            // Process linked entities that are connected without a builder
            processLinkedEntities(builder)
            checkInitialization() // TODO uncomment and check failed tests
        }
    
        fun checkInitialization() {
            val _diff = diff
            if (_diff != null) {
                if (_diff.extractOneToOneParent<WorkspaceEntityBase>(CONTENTROOTENTITY_CONNECTION_ID, this) == null) {
                    error("Field SourceRootOrderEntity#contentRootEntity should be initialized")
                }
            }
            else {
                if (this.entityLinks[EntityLink(false, CONTENTROOTENTITY_CONNECTION_ID)] == null) {
                    error("Field SourceRootOrderEntity#contentRootEntity should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field SourceRootOrderEntity#entitySource should be initialized")
            }
            if (!getEntityData().isOrderOfSourceRootsInitialized()) {
                error("Field SourceRootOrderEntity#orderOfSourceRoots should be initialized")
            }
        }
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }
    
        
        override var contentRootEntity: ContentRootEntity
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToOneParent(CONTENTROOTENTITY_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false, CONTENTROOTENTITY_CONNECTION_ID)]!! as ContentRootEntity
                } else {
                    this.entityLinks[EntityLink(false, CONTENTROOTENTITY_CONNECTION_ID)]!! as ContentRootEntity
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(true, CONTENTROOTENTITY_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToOneParentOfChild(CONTENTROOTENTITY_CONNECTION_ID, this, value)
                }
                else {
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        value.entityLinks[EntityLink(true, CONTENTROOTENTITY_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this.entityLinks[EntityLink(false, CONTENTROOTENTITY_CONNECTION_ID)] = value
                }
                changedProperty.add("contentRootEntity")
            }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var orderOfSourceRoots: List<VirtualFileUrl>
            get() = getEntityData().orderOfSourceRoots
            set(value) {
                checkModificationAllowed()
                getEntityData().orderOfSourceRoots = value
                val _diff = diff
                if (_diff != null) index(this, "orderOfSourceRoots", value.toHashSet())
                changedProperty.add("orderOfSourceRoots")
            }
        
        override fun getEntityData(): SourceRootOrderEntityData = result ?: super.getEntityData() as SourceRootOrderEntityData
        override fun getEntityClass(): Class<SourceRootOrderEntity> = SourceRootOrderEntity::class.java
    }
}
    
class SourceRootOrderEntityData : WorkspaceEntityData<SourceRootOrderEntity>() {
    lateinit var orderOfSourceRoots: List<VirtualFileUrl>

    fun isOrderOfSourceRootsInitialized(): Boolean = ::orderOfSourceRoots.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<SourceRootOrderEntity> {
        val modifiable = SourceRootOrderEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): SourceRootOrderEntity {
        val entity = SourceRootOrderEntityImpl()
        entity._orderOfSourceRoots = orderOfSourceRoots
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return SourceRootOrderEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SourceRootOrderEntityData
        
        if (this.entitySource != other.entitySource) return false
        if (this.orderOfSourceRoots != other.orderOfSourceRoots) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SourceRootOrderEntityData
        
        if (this.orderOfSourceRoots != other.orderOfSourceRoots) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + orderOfSourceRoots.hashCode()
        return result
    }
}