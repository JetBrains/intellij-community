package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder

    

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class SampleEntityImpl: SampleEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(SampleEntity::class.java, ChildSampleEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
    }
        
    override var booleanProperty: Boolean = false
    @JvmField var _stringProperty: String? = null
    override val stringProperty: String
        get() = _stringProperty!!
                        
    @JvmField var _stringListProperty: List<String>? = null
    override val stringListProperty: List<String>
        get() = _stringListProperty!!   
    
    @JvmField var _fileProperty: VirtualFileUrl? = null
    override val fileProperty: VirtualFileUrl
        get() = _fileProperty!!
                        
    override val children: List<ChildSampleEntity>
        get() = snapshot.extractOneToManyChildren<ChildSampleEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()
    
    @JvmField var _nullableData: String? = null
    override val nullableData: String?
        get() = _nullableData

    class Builder(val result: SampleEntityData?): ModifiableWorkspaceEntityBase<SampleEntity>(), SampleEntity.Builder {
        constructor(): this(SampleEntityData())
                 
        override fun build(): SampleEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity SampleEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            index(this, "fileProperty", this.fileProperty)
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
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field SampleEntity#entitySource should be initialized")
            }
            if (!getEntityData().isStringPropertyInitialized()) {
                error("Field SampleEntity#stringProperty should be initialized")
            }
            if (!getEntityData().isStringListPropertyInitialized()) {
                error("Field SampleEntity#stringListProperty should be initialized")
            }
            if (!getEntityData().isFilePropertyInitialized()) {
                error("Field SampleEntity#fileProperty should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
                    error("Field SampleEntity#children should be initialized")
                }
            }
            else {
                if (_children == null) {
                    error("Field SampleEntity#children should be initialized")
                }
            }
        }
    
        
        override var booleanProperty: Boolean
            get() = getEntityData().booleanProperty
            set(value) {
                checkModificationAllowed()
                getEntityData().booleanProperty = value
                changedProperty.add("booleanProperty")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var stringProperty: String
            get() = getEntityData().stringProperty
            set(value) {
                checkModificationAllowed()
                getEntityData().stringProperty = value
                changedProperty.add("stringProperty")
            }
            
        override var stringListProperty: List<String>
            get() = getEntityData().stringListProperty
            set(value) {
                checkModificationAllowed()
                getEntityData().stringListProperty = value
                
                changedProperty.add("stringListProperty")
            }
            
        override var fileProperty: VirtualFileUrl
            get() = getEntityData().fileProperty
            set(value) {
                checkModificationAllowed()
                getEntityData().fileProperty = value
                changedProperty.add("fileProperty")
                val _diff = diff
                if (_diff != null) index(this, "fileProperty", value)
            }
            
            var _children: List<ChildSampleEntity>? = null
            override var children: List<ChildSampleEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyChildren<ChildSampleEntity>(CHILDREN_CONNECTION_ID, this)!!.toList() + (_children ?: emptyList())
                    } else {
                        _children!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null) {
                        for (item_value in value) {
                            if (item_value is ModifiableWorkspaceEntityBase<*> && (item_value as? ModifiableWorkspaceEntityBase<*>)?.diff == null) {
                                _diff.addEntity(item_value)
                            }
                        }
                        _diff.updateOneToManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value)
                    }
                    else {
                        for (item_value in value) {
                            if (item_value is ChildSampleEntityImpl.Builder) {
                                item_value._parentEntity = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _children = value
                        // Test
                    }
                    changedProperty.add("children")
                }
        
        override var nullableData: String?
            get() = getEntityData().nullableData
            set(value) {
                checkModificationAllowed()
                getEntityData().nullableData = value
                changedProperty.add("nullableData")
            }
        
        override fun getEntityData(): SampleEntityData = result ?: super.getEntityData() as SampleEntityData
        override fun getEntityClass(): Class<SampleEntity> = SampleEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(SampleEntityData())
}
    
class SampleEntityData : WorkspaceEntityData<SampleEntity>() {
    var booleanProperty: Boolean = false
    lateinit var stringProperty: String
    lateinit var stringListProperty: List<String>
    lateinit var fileProperty: VirtualFileUrl
    var nullableData: String? = null

    
    fun isStringPropertyInitialized(): Boolean = ::stringProperty.isInitialized
    fun isStringListPropertyInitialized(): Boolean = ::stringListProperty.isInitialized
    fun isFilePropertyInitialized(): Boolean = ::fileProperty.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<SampleEntity> {
        val modifiable = SampleEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): SampleEntity {
        val entity = SampleEntityImpl()
        entity.booleanProperty = booleanProperty
        entity._stringProperty = stringProperty
        entity._stringListProperty = stringListProperty
        entity._fileProperty = fileProperty
        entity._nullableData = nullableData
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return SampleEntity::class.java
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SampleEntityData
        
        if (this.booleanProperty != other.booleanProperty) return false
        if (this.entitySource != other.entitySource) return false
        if (this.stringProperty != other.stringProperty) return false
        if (this.stringListProperty != other.stringListProperty) return false
        if (this.fileProperty != other.fileProperty) return false
        if (this.nullableData != other.nullableData) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SampleEntityData
        
        if (this.booleanProperty != other.booleanProperty) return false
        if (this.stringProperty != other.stringProperty) return false
        if (this.stringListProperty != other.stringListProperty) return false
        if (this.fileProperty != other.fileProperty) return false
        if (this.nullableData != other.nullableData) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + booleanProperty.hashCode()
        result = 31 * result + stringProperty.hashCode()
        result = 31 * result + stringListProperty.hashCode()
        result = 31 * result + fileProperty.hashCode()
        result = 31 * result + nullableData.hashCode()
        return result
    }
}