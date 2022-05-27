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
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.SoftLinkable
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class WithListSoftLinksEntityImpl: WithListSoftLinksEntity, WorkspaceEntityBase() {
    
        
    @JvmField var _myName: String? = null
    override val myName: String
        get() = _myName!!
                        
    @JvmField var _links: List<NameId>? = null
    override val links: List<NameId>
        get() = _links!!

    class Builder(val result: WithListSoftLinksEntityData?): ModifiableWorkspaceEntityBase<WithListSoftLinksEntity>(), WithListSoftLinksEntity.Builder {
        constructor(): this(WithListSoftLinksEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity WithListSoftLinksEntity is already created in a different builder")
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
            if (!getEntityData().isMyNameInitialized()) {
                error("Field WithListSoftLinksEntity#myName should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field WithListSoftLinksEntity#entitySource should be initialized")
            }
            if (!getEntityData().isLinksInitialized()) {
                error("Field WithListSoftLinksEntity#links should be initialized")
            }
        }
    
        
        override var myName: String
            get() = getEntityData().myName
            set(value) {
                checkModificationAllowed()
                getEntityData().myName = value
                changedProperty.add("myName")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var links: List<NameId>
            get() = getEntityData().links
            set(value) {
                checkModificationAllowed()
                getEntityData().links = value
                
                changedProperty.add("links")
            }
        
        override fun getEntityData(): WithListSoftLinksEntityData = result ?: super.getEntityData() as WithListSoftLinksEntityData
        override fun getEntityClass(): Class<WithListSoftLinksEntity> = WithListSoftLinksEntity::class.java
    }
}
    
class WithListSoftLinksEntityData : WorkspaceEntityData.WithCalculablePersistentId<WithListSoftLinksEntity>(), SoftLinkable {
    lateinit var myName: String
    lateinit var links: List<NameId>

    fun isMyNameInitialized(): Boolean = ::myName.isInitialized
    fun isLinksInitialized(): Boolean = ::links.isInitialized

    override fun getLinks(): Set<PersistentEntityId<*>> {
        val result = HashSet<PersistentEntityId<*>>()
        for (item in links) {
            result.add(item)
        }
        return result
    }

    override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
        for (item in links) {
            index.index(this, item)
        }
    }

    override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
        // TODO verify logic
        val mutablePreviousSet = HashSet(prev)
        for (item in links) {
            val removedItem_item = mutablePreviousSet.remove(item)
            if (!removedItem_item) {
                index.index(this, item)
            }
        }
        for (removed in mutablePreviousSet) {
            index.remove(this, removed)
        }
    }

    override fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean {
        var changed = false
        val links_data = links.map {
            val it_data =             if (it == oldLink) {
                changed = true
                newLink as NameId
            }
            else {
                null
            }
            if (it_data != null) {
                it_data
            }
            else {
                it
            }
        }
        if (links_data != null) {
            links = links_data
        }
        return changed
    }

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<WithListSoftLinksEntity> {
        val modifiable = WithListSoftLinksEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): WithListSoftLinksEntity {
        val entity = WithListSoftLinksEntityImpl()
        entity._myName = myName
        entity._links = links
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun persistentId(): PersistentEntityId<*> {
        return AnotherNameId(myName)
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return WithListSoftLinksEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as WithListSoftLinksEntityData
        
        if (this.myName != other.myName) return false
        if (this.entitySource != other.entitySource) return false
        if (this.links != other.links) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as WithListSoftLinksEntityData
        
        if (this.myName != other.myName) return false
        if (this.links != other.links) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + myName.hashCode()
        result = 31 * result + links.hashCode()
        return result
    }
}