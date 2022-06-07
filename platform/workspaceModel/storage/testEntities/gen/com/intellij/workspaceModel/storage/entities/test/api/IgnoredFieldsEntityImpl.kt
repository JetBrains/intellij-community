package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.deft.api.annotations.Ignore
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class IgnoredFieldsEntityImpl: IgnoredFieldsEntity, WorkspaceEntityBase() {
    
        
    @JvmField var _descriptor: AnotherDataClass? = null
    override val descriptor: AnotherDataClass
        get() = _descriptor!!
                        
    override var description: String = super<IgnoredFieldsEntity>.description
    
    override var anotherVersion: Int = super<IgnoredFieldsEntity>.anotherVersion

    class Builder(val result: IgnoredFieldsEntityData?): ModifiableWorkspaceEntityBase<IgnoredFieldsEntity>(), IgnoredFieldsEntity.Builder {
        constructor(): this(IgnoredFieldsEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity IgnoredFieldsEntity is already created in a different builder")
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
            if (!getEntityData().isDescriptorInitialized()) {
                error("Field IgnoredFieldsEntity#descriptor should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field IgnoredFieldsEntity#entitySource should be initialized")
            }
        }
    
        
        override var descriptor: AnotherDataClass
            get() = getEntityData().descriptor
            set(value) {
                checkModificationAllowed()
                getEntityData().descriptor = value
                changedProperty.add("descriptor")
                
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var description: String
            get() = getEntityData().description
            set(value) {
                checkModificationAllowed()
                getEntityData().description = value
                changedProperty.add("description")
            }
            
        override var anotherVersion: Int
            get() = getEntityData().anotherVersion
            set(value) {
                checkModificationAllowed()
                getEntityData().anotherVersion = value
                changedProperty.add("anotherVersion")
            }
        
        override fun getEntityData(): IgnoredFieldsEntityData = result ?: super.getEntityData() as IgnoredFieldsEntityData
        override fun getEntityClass(): Class<IgnoredFieldsEntity> = IgnoredFieldsEntity::class.java
    }
}
    
class IgnoredFieldsEntityData : WorkspaceEntityData<IgnoredFieldsEntity>() {
    lateinit var descriptor: AnotherDataClass
    var description: String = "Default description"
    var anotherVersion: Int = 0

    fun isDescriptorInitialized(): Boolean = ::descriptor.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<IgnoredFieldsEntity> {
        val modifiable = IgnoredFieldsEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): IgnoredFieldsEntity {
        val entity = IgnoredFieldsEntityImpl()
        entity._descriptor = descriptor
        entity.description = description
        entity.anotherVersion = anotherVersion
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return IgnoredFieldsEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as IgnoredFieldsEntityData
        
        if (this.descriptor != other.descriptor) return false
        if (this.entitySource != other.entitySource) return false
        if (this.description != other.description) return false
        if (this.anotherVersion != other.anotherVersion) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as IgnoredFieldsEntityData
        
        if (this.descriptor != other.descriptor) return false
        if (this.description != other.description) return false
        if (this.anotherVersion != other.anotherVersion) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + descriptor.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + anotherVersion.hashCode()
        return result
    }
}