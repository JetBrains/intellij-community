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
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import com.intellij.workspaceModel.storage.referrersx
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class FacetsOrderEntityImpl: FacetsOrderEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val MODULEENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, FacetsOrderEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
        
    @JvmField var _orderOfFacets: List<String>? = null
    override val orderOfFacets: List<String>
        get() = _orderOfFacets!!   
    
    override val moduleEntity: ModuleEntity
        get() = snapshot.extractOneToOneParent(MODULEENTITY_CONNECTION_ID, this)!!

    class Builder(val result: FacetsOrderEntityData?): ModifiableWorkspaceEntityBase<FacetsOrderEntity>(), FacetsOrderEntity.Builder {
        constructor(): this(FacetsOrderEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity FacetsOrderEntity is already created in a different builder")
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
            val __moduleEntity = _moduleEntity
            if (__moduleEntity != null && (__moduleEntity is ModifiableWorkspaceEntityBase<*>) && __moduleEntity.diff == null) {
                builder.addEntity(__moduleEntity)
            }
            if (__moduleEntity != null && (__moduleEntity is ModifiableWorkspaceEntityBase<*>) && __moduleEntity.diff != null) {
                // Set field to null (in referenced entity)
                __moduleEntity.extReferences.remove(ExtRefKey("FacetsOrderEntity", "moduleEntity", true, MODULEENTITY_CONNECTION_ID))
            }
            if (__moduleEntity != null) {
                applyParentRef(MODULEENTITY_CONNECTION_ID, __moduleEntity)
                this._moduleEntity = null
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
            if (!getEntityData().isOrderOfFacetsInitialized()) {
                error("Field FacetsOrderEntity#orderOfFacets should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field FacetsOrderEntity#entitySource should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToOneParent<WorkspaceEntityBase>(MODULEENTITY_CONNECTION_ID, this) == null) {
                    error("Field FacetsOrderEntity#moduleEntity should be initialized")
                }
            }
            else {
                if (_moduleEntity == null) {
                    error("Field FacetsOrderEntity#moduleEntity should be initialized")
                }
            }
        }
    
        
        override var orderOfFacets: List<String>
            get() = getEntityData().orderOfFacets
            set(value) {
                checkModificationAllowed()
                getEntityData().orderOfFacets = value
                
                changedProperty.add("orderOfFacets")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
            var _moduleEntity: ModuleEntity? = null
            override var moduleEntity: ModuleEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneParent(MODULEENTITY_CONNECTION_ID, this) ?: _moduleEntity!!
                    } else {
                        _moduleEntity!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ModuleEntityImpl.Builder) {
                            value.extReferences[ExtRefKey("FacetsOrderEntity", "moduleEntity", true, MODULEENTITY_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(MODULEENTITY_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ModuleEntityImpl.Builder) {
                            value.extReferences[ExtRefKey("FacetsOrderEntity", "moduleEntity", true, MODULEENTITY_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._moduleEntity = value
                    }
                    changedProperty.add("moduleEntity")
                }
        
        override fun getEntityData(): FacetsOrderEntityData = result ?: super.getEntityData() as FacetsOrderEntityData
        override fun getEntityClass(): Class<FacetsOrderEntity> = FacetsOrderEntity::class.java
    }
}
    
class FacetsOrderEntityData : WorkspaceEntityData<FacetsOrderEntity>() {
    lateinit var orderOfFacets: List<String>

    fun isOrderOfFacetsInitialized(): Boolean = ::orderOfFacets.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<FacetsOrderEntity> {
        val modifiable = FacetsOrderEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): FacetsOrderEntity {
        val entity = FacetsOrderEntityImpl()
        entity._orderOfFacets = orderOfFacets
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return FacetsOrderEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
        ser.saveInt(orderOfFacets.size)
        for (_orderOfFacets in orderOfFacets) {
            ser.saveString(_orderOfFacets)
        }
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
        val counter0 = de.readInt()
        val collector1 = ArrayList<String>()
        var _orderOfFacets: String
        repeat(counter0) {
            _orderOfFacets = de.readString()
            collector1.add(_orderOfFacets)
        }
        orderOfFacets = collector1
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as FacetsOrderEntityData
        
        if (this.orderOfFacets != other.orderOfFacets) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as FacetsOrderEntityData
        
        if (this.orderOfFacets != other.orderOfFacets) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + orderOfFacets.hashCode()
        return result
    }
}