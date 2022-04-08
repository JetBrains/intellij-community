package com.intellij.workspaceModel.storage.entities.test.api

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
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import org.jetbrains.deft.ObjBuilder

    

open class SimpleChildAbstractEntityImpl: SimpleChildAbstractEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val PARENTINLIST_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositeAbstractEntity::class.java, SimpleAbstractEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
    }
        
    override val parentInList: CompositeAbstractEntity
        get() = snapshot.extractOneToAbstractManyParent(PARENTINLIST_CONNECTION_ID, this)!!

    class Builder(val result: SimpleChildAbstractEntityData?): ModifiableWorkspaceEntityBase<SimpleChildAbstractEntity>(), SimpleChildAbstractEntity.Builder {
        constructor(): this(SimpleChildAbstractEntityData())
                 
        override fun build(): SimpleChildAbstractEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity SimpleChildAbstractEntity is already created in a different builder")
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
            val __parentInList = _parentInList
            if (__parentInList != null && (__parentInList is ModifiableWorkspaceEntityBase<*>) && __parentInList.diff == null) {
                builder.addEntity(__parentInList)
            }
            if (__parentInList != null && (__parentInList is ModifiableWorkspaceEntityBase<*>) && __parentInList.diff != null) {
                // Set field to null (in referenced entity)
                val access = __parentInList::class.memberProperties.single { it.name == "_children" } as KMutableProperty1<*, *>
                val __mutChildren = (access.getter.call(__parentInList) as? List<*>)?.toMutableList()
                __mutChildren?.remove(this)
                access.setter.call(__parentInList, if (__mutChildren.isNullOrEmpty()) null else __mutChildren)
            }
            if (__parentInList != null) {
                applyParentRef(PARENTINLIST_CONNECTION_ID, __parentInList)
                this._parentInList = null
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
            if (_diff != null) {
                if (_diff.extractOneToAbstractManyParent<WorkspaceEntityBase>(PARENTINLIST_CONNECTION_ID, this) == null) {
                    error("Field SimpleAbstractEntity#parentInList should be initialized")
                }
            }
            else {
                if (_parentInList == null) {
                    error("Field SimpleAbstractEntity#parentInList should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field SimpleAbstractEntity#entitySource should be initialized")
            }
        }
    
        
            var _parentInList: CompositeAbstractEntity? = null
            override var parentInList: CompositeAbstractEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToAbstractManyParent(PARENTINLIST_CONNECTION_ID, this) ?: _parentInList!!
                    } else {
                        _parentInList!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value != null) {
                            val access = value::class.memberProperties.single { it.name == "_children" } as KMutableProperty1<*, *>
                            access.setter.call(value, ((access.getter.call(value) as? List<*>) ?: emptyList<Any>()) + this)
                        }
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToAbstractManyParentOfChild(PARENTINLIST_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value != null) {
                            val access = value::class.memberProperties.single { it.name == "_children" } as KMutableProperty1<*, *>
                            access.setter.call(value, ((access.getter.call(value) as? List<*>) ?: emptyList<Any>()) + this)
                        }
                        
                        this._parentInList = value
                    }
                    changedProperty.add("parentInList")
                }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
        
        override fun getEntityData(): SimpleChildAbstractEntityData = result ?: super.getEntityData() as SimpleChildAbstractEntityData
        override fun getEntityClass(): Class<SimpleChildAbstractEntity> = SimpleChildAbstractEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(SimpleChildAbstractEntityData())
}
    
class SimpleChildAbstractEntityData : WorkspaceEntityData<SimpleChildAbstractEntity>() {


    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<SimpleChildAbstractEntity> {
        val modifiable = SimpleChildAbstractEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): SimpleChildAbstractEntity {
        val entity = SimpleChildAbstractEntityImpl()
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SimpleChildAbstractEntityData
        
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SimpleChildAbstractEntityData
        
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        return result
    }
}