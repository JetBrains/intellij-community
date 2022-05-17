package com.intellij.workspaceModel.storage.entities.test.api

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
open class DefaultValueEntityImpl: DefaultValueEntity, WorkspaceEntityBase() {
    
        
    @JvmField var _name: String? = null
    override val name: String
        get() = _name!!
                        
    override var isGenerated: Boolean = super<DefaultValueEntity>.isGenerated
    
    override var anotherName: String = super<DefaultValueEntity>.anotherName

    class Builder(val result: DefaultValueEntityData?): ModifiableWorkspaceEntityBase<DefaultValueEntity>(), DefaultValueEntity.Builder {
        constructor(): this(DefaultValueEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity DefaultValueEntity is already created in a different builder")
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
            if (!getEntityData().isNameInitialized()) {
                error("Field DefaultValueEntity#name should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field DefaultValueEntity#entitySource should be initialized")
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
            
        override var isGenerated: Boolean
            get() = getEntityData().isGenerated
            set(value) {
                checkModificationAllowed()
                getEntityData().isGenerated = value
                changedProperty.add("isGenerated")
            }
            
        override var anotherName: String
            get() = getEntityData().anotherName
            set(value) {
                checkModificationAllowed()
                getEntityData().anotherName = value
                changedProperty.add("anotherName")
            }
        
        override fun getEntityData(): DefaultValueEntityData = result ?: super.getEntityData() as DefaultValueEntityData
        override fun getEntityClass(): Class<DefaultValueEntity> = DefaultValueEntity::class.java
    }
}
    
class DefaultValueEntityData : WorkspaceEntityData<DefaultValueEntity>() {
    lateinit var name: String
    var isGenerated: Boolean = true
    var anotherName: String = "Another Text"

    fun isNameInitialized(): Boolean = ::name.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<DefaultValueEntity> {
        val modifiable = DefaultValueEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): DefaultValueEntity {
        val entity = DefaultValueEntityImpl()
        entity._name = name
        entity.isGenerated = isGenerated
        entity.anotherName = anotherName
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return DefaultValueEntity::class.java
    }

    fun serialize(ser: EntityInformation.Serializer) {
        ser.saveString(name)
        ser.saveBoolean(isGenerated)
        ser.saveString(anotherName)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as DefaultValueEntityData
        
        if (this.name != other.name) return false
        if (this.entitySource != other.entitySource) return false
        if (this.isGenerated != other.isGenerated) return false
        if (this.anotherName != other.anotherName) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as DefaultValueEntityData
        
        if (this.name != other.name) return false
        if (this.isGenerated != other.isGenerated) return false
        if (this.anotherName != other.anotherName) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isGenerated.hashCode()
        result = 31 * result + anotherName.hashCode()
        return result
    }
}