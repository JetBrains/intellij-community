package com.intellij.workspaceModel.storage.entities.test.api

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
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import java.util.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class SampleWithPersistentIdEntityImpl: SampleWithPersistentIdEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(SampleWithPersistentIdEntity::class.java, ChildWpidSampleEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
        
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
                        
    override val children: List<ChildWpidSampleEntity>
        get() = snapshot.extractOneToManyChildren<ChildWpidSampleEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()
    
    @JvmField var _nullableData: String? = null
    override val nullableData: String?
        get() = _nullableData
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: SampleWithPersistentIdEntityData?): ModifiableWorkspaceEntityBase<SampleWithPersistentIdEntity>(), SampleWithPersistentIdEntity.Builder {
        constructor(): this(SampleWithPersistentIdEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity SampleWithPersistentIdEntity is already created in a different builder")
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
                error("Field SampleWithPersistentIdEntity#entitySource should be initialized")
            }
            if (!getEntityData().isStringPropertyInitialized()) {
                error("Field SampleWithPersistentIdEntity#stringProperty should be initialized")
            }
            if (!getEntityData().isStringListPropertyInitialized()) {
                error("Field SampleWithPersistentIdEntity#stringListProperty should be initialized")
            }
            if (!getEntityData().isStringMapPropertyInitialized()) {
                error("Field SampleWithPersistentIdEntity#stringMapProperty should be initialized")
            }
            if (!getEntityData().isFilePropertyInitialized()) {
                error("Field SampleWithPersistentIdEntity#fileProperty should be initialized")
            }
            // Check initialization for collection with ref type
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
                    error("Field SampleWithPersistentIdEntity#children should be initialized")
                }
            }
            else {
                if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
                    error("Field SampleWithPersistentIdEntity#children should be initialized")
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
        var _children: List<ChildWpidSampleEntity>? = emptyList()
        override var children: List<ChildWpidSampleEntity>
            get() {
                // Getter of the list of non-abstract referenced types
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToManyChildren<ChildWpidSampleEntity>(CHILDREN_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<ChildWpidSampleEntity> ?: emptyList())
                } else {
                    this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<ChildWpidSampleEntity> ?: emptyList()
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
        
        override fun getEntityData(): SampleWithPersistentIdEntityData = result ?: super.getEntityData() as SampleWithPersistentIdEntityData
        override fun getEntityClass(): Class<SampleWithPersistentIdEntity> = SampleWithPersistentIdEntity::class.java
    }
}
    
class SampleWithPersistentIdEntityData : WorkspaceEntityData.WithCalculablePersistentId<SampleWithPersistentIdEntity>() {
    var booleanProperty: Boolean = false
    lateinit var stringProperty: String
    lateinit var stringListProperty: List<String>
    lateinit var stringMapProperty: Map<String, String>
    lateinit var fileProperty: VirtualFileUrl
    var nullableData: String? = null

    
    fun isStringPropertyInitialized(): Boolean = ::stringProperty.isInitialized
    fun isStringListPropertyInitialized(): Boolean = ::stringListProperty.isInitialized
    fun isStringMapPropertyInitialized(): Boolean = ::stringMapProperty.isInitialized
    fun isFilePropertyInitialized(): Boolean = ::fileProperty.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<SampleWithPersistentIdEntity> {
        val modifiable = SampleWithPersistentIdEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): SampleWithPersistentIdEntity {
        val entity = SampleWithPersistentIdEntityImpl()
        entity.booleanProperty = booleanProperty
        entity._stringProperty = stringProperty
        entity._stringListProperty = stringListProperty
        entity._stringMapProperty = stringMapProperty
        entity._fileProperty = fileProperty
        entity._nullableData = nullableData
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun persistentId(): PersistentEntityId<*> {
        return SamplePersistentId(stringProperty)
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return SampleWithPersistentIdEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SampleWithPersistentIdEntityData
        
        if (this.booleanProperty != other.booleanProperty) return false
        if (this.entitySource != other.entitySource) return false
        if (this.stringProperty != other.stringProperty) return false
        if (this.stringListProperty != other.stringListProperty) return false
        if (this.stringMapProperty != other.stringMapProperty) return false
        if (this.fileProperty != other.fileProperty) return false
        if (this.nullableData != other.nullableData) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SampleWithPersistentIdEntityData
        
        if (this.booleanProperty != other.booleanProperty) return false
        if (this.stringProperty != other.stringProperty) return false
        if (this.stringListProperty != other.stringListProperty) return false
        if (this.stringMapProperty != other.stringMapProperty) return false
        if (this.fileProperty != other.fileProperty) return false
        if (this.nullableData != other.nullableData) return false
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
        return result
    }
}