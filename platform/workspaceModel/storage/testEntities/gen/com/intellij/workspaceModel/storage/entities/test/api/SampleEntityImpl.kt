package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
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
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import java.util.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class SampleEntityImpl: SampleEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(SampleEntity::class.java, ChildSampleEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
        
        val connections = listOf<ConnectionId>(
            CHILDREN_CONNECTION_ID,
        )

    }
        
    override var booleanProperty: Boolean = false
    @JvmField var _stringProperty: String? = null
    override val stringProperty: String
        get() = _stringProperty!!
                        
    @JvmField var _stringListProperty: List<String>? = null
    override val stringListProperty: List<String>
        get() = _stringListProperty!!   
    
    @JvmField var _stringMapProperty: Map<String, String>? = null
    override val stringMapProperty: Map<String, String>
        get() = _stringMapProperty!!
    @JvmField var _fileProperty: VirtualFileUrl? = null
    override val fileProperty: VirtualFileUrl
        get() = _fileProperty!!
                        
    override val children: List<ChildSampleEntity>
        get() = snapshot.extractOneToManyChildren<ChildSampleEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()
    
    @JvmField var _nullableData: String? = null
    override val nullableData: String?
        get() = _nullableData
                        
    @JvmField var _randomUUID: UUID? = null
    override val randomUUID: UUID?
        get() = _randomUUID
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: SampleEntityData?): ModifiableWorkspaceEntityBase<SampleEntity>(), SampleEntity.Builder {
        constructor(): this(SampleEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
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
            // Process linked entities that are connected without a builder
            processLinkedEntities(builder)
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
            if (!getEntityData().isStringMapPropertyInitialized()) {
                error("Field SampleEntity#stringMapProperty should be initialized")
            }
            if (!getEntityData().isFilePropertyInitialized()) {
                error("Field SampleEntity#fileProperty should be initialized")
            }
            // Check initialization for collection with ref type
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
                    error("Field SampleEntity#children should be initialized")
                }
            }
            else {
                if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
                    error("Field SampleEntity#children should be initialized")
                }
            }
        }
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
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
            
        override var stringMapProperty: Map<String, String>
            get() = getEntityData().stringMapProperty
            set(value) {
                checkModificationAllowed()
                getEntityData().stringMapProperty = value
                changedProperty.add("stringMapProperty")
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
            
        // List of non-abstract referenced types
        var _children: List<ChildSampleEntity>? = emptyList()
        override var children: List<ChildSampleEntity>
            get() {
                // Getter of the list of non-abstract referenced types
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToManyChildren<ChildSampleEntity>(CHILDREN_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<ChildSampleEntity> ?: emptyList())
                } else {
                    this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<ChildSampleEntity> ?: emptyList()
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
                    _diff.updateOneToManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value)
                }
                else {
                    for (item_value in value) {
                        if (item_value is ModifiableWorkspaceEntityBase<*>) {
                            item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                    }
                    
                    this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] = value
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
            
        override var randomUUID: UUID?
            get() = getEntityData().randomUUID
            set(value) {
                checkModificationAllowed()
                getEntityData().randomUUID = value
                changedProperty.add("randomUUID")
                
            }
        
        override fun getEntityData(): SampleEntityData = result ?: super.getEntityData() as SampleEntityData
        override fun getEntityClass(): Class<SampleEntity> = SampleEntity::class.java
    }
}
    
class SampleEntityData : WorkspaceEntityData<SampleEntity>() {
    var booleanProperty: Boolean = false
    lateinit var stringProperty: String
    lateinit var stringListProperty: List<String>
    lateinit var stringMapProperty: Map<String, String>
    lateinit var fileProperty: VirtualFileUrl
    var nullableData: String? = null
    var randomUUID: UUID? = null

    
    fun isStringPropertyInitialized(): Boolean = ::stringProperty.isInitialized
    fun isStringListPropertyInitialized(): Boolean = ::stringListProperty.isInitialized
    fun isStringMapPropertyInitialized(): Boolean = ::stringMapProperty.isInitialized
    fun isFilePropertyInitialized(): Boolean = ::fileProperty.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<SampleEntity> {
        val modifiable = SampleEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): SampleEntity {
        val entity = SampleEntityImpl()
        entity.booleanProperty = booleanProperty
        entity._stringProperty = stringProperty
        entity._stringListProperty = stringListProperty
        entity._stringMapProperty = stringMapProperty
        entity._fileProperty = fileProperty
        entity._nullableData = nullableData
        entity._randomUUID = randomUUID
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return SampleEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SampleEntityData
        
        if (this.booleanProperty != other.booleanProperty) return false
        if (this.entitySource != other.entitySource) return false
        if (this.stringProperty != other.stringProperty) return false
        if (this.stringListProperty != other.stringListProperty) return false
        if (this.stringMapProperty != other.stringMapProperty) return false
        if (this.fileProperty != other.fileProperty) return false
        if (this.nullableData != other.nullableData) return false
        if (this.randomUUID != other.randomUUID) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SampleEntityData
        
        if (this.booleanProperty != other.booleanProperty) return false
        if (this.stringProperty != other.stringProperty) return false
        if (this.stringListProperty != other.stringListProperty) return false
        if (this.stringMapProperty != other.stringMapProperty) return false
        if (this.fileProperty != other.fileProperty) return false
        if (this.nullableData != other.nullableData) return false
        if (this.randomUUID != other.randomUUID) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + booleanProperty.hashCode()
        result = 31 * result + stringProperty.hashCode()
        result = 31 * result + stringListProperty.hashCode()
        result = 31 * result + stringMapProperty.hashCode()
        result = 31 * result + fileProperty.hashCode()
        result = 31 * result + nullableData.hashCode()
        result = 31 * result + randomUUID.hashCode()
        return result
    }
}