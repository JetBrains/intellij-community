// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

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
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class XParentEntityImpl: XParentEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(XParentEntity::class.java, XChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        internal val OPTIONALCHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(XParentEntity::class.java, XChildWithOptionalParentEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
        internal val CHILDCHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(XParentEntity::class.java, XChildChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    }
        
    @JvmField var _parentProperty: String? = null
    override val parentProperty: String
        get() = _parentProperty!!
                        
    override val children: List<XChildEntity>
        get() = snapshot.extractOneToManyChildren<XChildEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()
    
    override val optionalChildren: List<XChildWithOptionalParentEntity>
        get() = snapshot.extractOneToManyChildren<XChildWithOptionalParentEntity>(OPTIONALCHILDREN_CONNECTION_ID, this)!!.toList()
    
    override val childChild: List<XChildChildEntity>
        get() = snapshot.extractOneToManyChildren<XChildChildEntity>(CHILDCHILD_CONNECTION_ID, this)!!.toList()

    class Builder(val result: XParentEntityData?): ModifiableWorkspaceEntityBase<XParentEntity>(), XParentEntity.Builder {
        constructor(): this(XParentEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity XParentEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            val __children = _children!!
            for (item in __children) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_children, woBuilder_children) = __children.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(CHILDREN_CONNECTION_ID, withBuilder_children)
            this._children = if (woBuilder_children.isNotEmpty()) woBuilder_children else null
            val __optionalChildren = _optionalChildren!!
            for (item in __optionalChildren) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_optionalChildren, woBuilder_optionalChildren) = __optionalChildren.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(OPTIONALCHILDREN_CONNECTION_ID, withBuilder_optionalChildren)
            this._optionalChildren = if (woBuilder_optionalChildren.isNotEmpty()) woBuilder_optionalChildren else null
            val __childChild = _childChild!!
            for (item in __childChild) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_childChild, woBuilder_childChild) = __childChild.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(CHILDCHILD_CONNECTION_ID, withBuilder_childChild)
            this._childChild = if (woBuilder_childChild.isNotEmpty()) woBuilder_childChild else null
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
            if (!getEntityData().isParentPropertyInitialized()) {
                error("Field XParentEntity#parentProperty should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field XParentEntity#entitySource should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
                    error("Field XParentEntity#children should be initialized")
                }
            }
            else {
                if (_children == null) {
                    error("Field XParentEntity#children should be initialized")
                }
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(OPTIONALCHILDREN_CONNECTION_ID, this) == null) {
                    error("Field XParentEntity#optionalChildren should be initialized")
                }
            }
            else {
                if (_optionalChildren == null) {
                    error("Field XParentEntity#optionalChildren should be initialized")
                }
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDCHILD_CONNECTION_ID, this) == null) {
                    error("Field XParentEntity#childChild should be initialized")
                }
            }
            else {
                if (_childChild == null) {
                    error("Field XParentEntity#childChild should be initialized")
                }
            }
        }
    
        
        override var parentProperty: String
            get() = getEntityData().parentProperty
            set(value) {
                checkModificationAllowed()
                getEntityData().parentProperty = value
                changedProperty.add("parentProperty")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
            var _children: List<XChildEntity>? = null
            override var children: List<XChildEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyChildren<XChildEntity>(CHILDREN_CONNECTION_ID, this)!!.toList() + (_children ?: emptyList())
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
                            if (item_value is XChildEntityImpl.Builder) {
                                item_value._parentEntity = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _children = value
                        // Test
                    }
                    changedProperty.add("children")
                }
        
            var _optionalChildren: List<XChildWithOptionalParentEntity>? = null
            override var optionalChildren: List<XChildWithOptionalParentEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyChildren<XChildWithOptionalParentEntity>(OPTIONALCHILDREN_CONNECTION_ID, this)!!.toList() + (_optionalChildren ?: emptyList())
                    } else {
                        _optionalChildren!!
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
                        _diff.updateOneToManyChildrenOfParent(OPTIONALCHILDREN_CONNECTION_ID, this, value)
                    }
                    else {
                        for (item_value in value) {
                            if (item_value is XChildWithOptionalParentEntityImpl.Builder) {
                                item_value._optionalParent = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _optionalChildren = value
                        // Test
                    }
                    changedProperty.add("optionalChildren")
                }
        
            var _childChild: List<XChildChildEntity>? = null
            override var childChild: List<XChildChildEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyChildren<XChildChildEntity>(CHILDCHILD_CONNECTION_ID, this)!!.toList() + (_childChild ?: emptyList())
                    } else {
                        _childChild!!
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
                        _diff.updateOneToManyChildrenOfParent(CHILDCHILD_CONNECTION_ID, this, value)
                    }
                    else {
                        for (item_value in value) {
                            if (item_value is XChildChildEntityImpl.Builder) {
                                item_value._parent1 = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _childChild = value
                        // Test
                    }
                    changedProperty.add("childChild")
                }
        
        override fun getEntityData(): XParentEntityData = result ?: super.getEntityData() as XParentEntityData
        override fun getEntityClass(): Class<XParentEntity> = XParentEntity::class.java
    }
}
    
class XParentEntityData : WorkspaceEntityData<XParentEntity>() {
    lateinit var parentProperty: String

    fun isParentPropertyInitialized(): Boolean = ::parentProperty.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<XParentEntity> {
        val modifiable = XParentEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): XParentEntity {
        val entity = XParentEntityImpl()
        entity._parentProperty = parentProperty
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return XParentEntity::class.java
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as XParentEntityData
        
        if (this.parentProperty != other.parentProperty) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as XParentEntityData
        
        if (this.parentProperty != other.parentProperty) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + parentProperty.hashCode()
        return result
    }
}