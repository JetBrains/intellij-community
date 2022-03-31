package model.testing

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
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class ChildSecondEntityImpl: ChildSecondEntity, WorkspaceEntityBase() {
    
    companion object {
        /* internal */val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentAbEntity::class.java, ChildAbstractBaseEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
    }
    
    override val factory: ObjType<*, *>
        get() = ChildSecondEntity
        
    @JvmField var _commonData: String? = null
    override val commonData: String
        get() = _commonData!!
                        
    override val parentEntity: ParentAbEntity
        get() = snapshot.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this)!!           
        
    @JvmField var _secondData: String? = null
    override val secondData: String
        get() = _secondData!!

    class Builder(val result: ChildSecondEntityData?): ModifiableWorkspaceEntityBase<ChildSecondEntity>(), ChildSecondEntity.Builder {
        constructor(): this(ChildSecondEntityData())
                 
        override val factory: ObjType<ChildSecondEntity, *> get() = TODO()
        override fun build(): ChildSecondEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ChildSecondEntity is already created in a different builder")
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
                val __mutChildren = (__parentEntity as ParentAbEntityImpl.Builder)._children?.toMutableList()
                __mutChildren?.remove(this)
                __parentEntity._children = if (__mutChildren.isNullOrEmpty()) null else __mutChildren
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
            if (!getEntityData().isCommonDataInitialized()) {
                error("Field ChildAbstractBaseEntity#commonData should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToAbstractManyParent<WorkspaceEntityBase>(PARENTENTITY_CONNECTION_ID, this) == null) {
                    error("Field ChildAbstractBaseEntity#parentEntity should be initialized")
                }
            }
            else {
                if (_parentEntity == null) {
                    error("Field ChildAbstractBaseEntity#parentEntity should be initialized")
                }
            }
            if (!getEntityData().isSecondDataInitialized()) {
                error("Field ChildSecondEntity#secondData should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ChildSecondEntity#entitySource should be initialized")
            }
        }
    
        
        override var commonData: String
            get() = getEntityData().commonData
            set(value) {
                checkModificationAllowed()
                getEntityData().commonData = value
                changedProperty.add("commonData")
            }
            
            var _parentEntity: ParentAbEntity? = null
            override var parentEntity: ParentAbEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this) ?: _parentEntity!!
                    } else {
                        _parentEntity!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ParentAbEntityImpl.Builder) {
                            value._children = (value._children ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToAbstractManyParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ParentAbEntityImpl.Builder) {
                            value._children = (value._children ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._parentEntity = value
                    }
                    changedProperty.add("parentEntity")
                }
        
        override var secondData: String
            get() = getEntityData().secondData
            set(value) {
                checkModificationAllowed()
                getEntityData().secondData = value
                changedProperty.add("secondData")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
        
        override fun hasNewValue(field: Field<in ChildSecondEntity, *>): Boolean = TODO("Not yet implemented")                                                                     
        override fun <V> setValue(field: Field<in ChildSecondEntity, V>, value: V) = TODO("Not yet implemented")
        override fun getEntityData(): ChildSecondEntityData = result ?: super.getEntityData() as ChildSecondEntityData
        override fun getEntityClass(): Class<ChildSecondEntity> = ChildSecondEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(ChildSecondEntityData())
}
    
class ChildSecondEntityData : WorkspaceEntityData<ChildSecondEntity>() {
    lateinit var commonData: String
    lateinit var secondData: String

    fun isCommonDataInitialized(): Boolean = ::commonData.isInitialized
    fun isSecondDataInitialized(): Boolean = ::secondData.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<ChildSecondEntity> {
        val modifiable = ChildSecondEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): ChildSecondEntity {
        val entity = ChildSecondEntityImpl()
        entity._commonData = commonData
        entity._secondData = secondData
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildSecondEntityData
        
        if (this.commonData != other.commonData) return false
        if (this.secondData != other.secondData) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ChildSecondEntityData
        
        if (this.commonData != other.commonData) return false
        if (this.secondData != other.secondData) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + commonData.hashCode()
        result = 31 * result + secondData.hashCode()
        return result
    }
}