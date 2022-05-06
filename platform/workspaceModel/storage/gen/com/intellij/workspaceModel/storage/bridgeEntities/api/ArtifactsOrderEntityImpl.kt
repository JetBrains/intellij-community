// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

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

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class ArtifactsOrderEntityImpl: ArtifactsOrderEntity, WorkspaceEntityBase() {
    
        
    @JvmField var _orderOfArtifacts: List<String>? = null
    override val orderOfArtifacts: List<String>
        get() = _orderOfArtifacts!!

    class Builder(val result: ArtifactsOrderEntityData?): ModifiableWorkspaceEntityBase<ArtifactsOrderEntity>(), ArtifactsOrderEntity.Builder {
        constructor(): this(ArtifactsOrderEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ArtifactsOrderEntity is already created in a different builder")
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
            if (!getEntityData().isOrderOfArtifactsInitialized()) {
                error("Field ArtifactsOrderEntity#orderOfArtifacts should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ArtifactsOrderEntity#entitySource should be initialized")
            }
        }
    
        
        override var orderOfArtifacts: List<String>
            get() = getEntityData().orderOfArtifacts
            set(value) {
                checkModificationAllowed()
                getEntityData().orderOfArtifacts = value
                
                changedProperty.add("orderOfArtifacts")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
        
        override fun getEntityData(): ArtifactsOrderEntityData = result ?: super.getEntityData() as ArtifactsOrderEntityData
        override fun getEntityClass(): Class<ArtifactsOrderEntity> = ArtifactsOrderEntity::class.java
    }
}
    
class ArtifactsOrderEntityData : WorkspaceEntityData<ArtifactsOrderEntity>() {
    lateinit var orderOfArtifacts: List<String>

    fun isOrderOfArtifactsInitialized(): Boolean = ::orderOfArtifacts.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ArtifactsOrderEntity> {
        val modifiable = ArtifactsOrderEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ArtifactsOrderEntity {
        val entity = ArtifactsOrderEntityImpl()
        entity._orderOfArtifacts = orderOfArtifacts
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ArtifactsOrderEntity::class.java
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ArtifactsOrderEntityData
        
        if (this.orderOfArtifacts != other.orderOfArtifacts) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ArtifactsOrderEntityData
        
        if (this.orderOfArtifacts != other.orderOfArtifacts) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + orderOfArtifacts.hashCode()
        return result
    }
}