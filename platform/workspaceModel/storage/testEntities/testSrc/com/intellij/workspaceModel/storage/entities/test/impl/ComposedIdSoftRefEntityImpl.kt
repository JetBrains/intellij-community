package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.SoftLinkable
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import org.jetbrains.deft.ObjBuilder

    

open class ComposedIdSoftRefEntityImpl: ComposedIdSoftRefEntity, WorkspaceEntityBase() {
    
        
    @JvmField var _myName: String? = null
    override val myName: String
        get() = _myName!!
                        
    @JvmField var _link: NameId? = null
    override val link: NameId
        get() = _link!!

    class Builder(val result: ComposedIdSoftRefEntityData?): ModifiableWorkspaceEntityBase<ComposedIdSoftRefEntity>(), ComposedIdSoftRefEntity.Builder {
        constructor(): this(ComposedIdSoftRefEntityData())
                 
        override fun build(): ComposedIdSoftRefEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ComposedIdSoftRefEntity is already created in a different builder")
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
                error("Field ComposedIdSoftRefEntity#myName should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ComposedIdSoftRefEntity#entitySource should be initialized")
            }
            if (!getEntityData().isLinkInitialized()) {
                error("Field ComposedIdSoftRefEntity#link should be initialized")
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
            
        override var link: NameId
            get() = getEntityData().link
            set(value) {
                checkModificationAllowed()
                getEntityData().link = value
                changedProperty.add("link")
                
            }
        
        override fun getEntityData(): ComposedIdSoftRefEntityData = result ?: super.getEntityData() as ComposedIdSoftRefEntityData
        override fun getEntityClass(): Class<ComposedIdSoftRefEntity> = ComposedIdSoftRefEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(ComposedIdSoftRefEntityData())
}
    
class ComposedIdSoftRefEntityData : WorkspaceEntityData.WithCalculablePersistentId<ComposedIdSoftRefEntity>(), SoftLinkable {
    lateinit var myName: String
    lateinit var link: NameId

    fun isMyNameInitialized(): Boolean = ::myName.isInitialized
    fun isLinkInitialized(): Boolean = ::link.isInitialized

    override fun getLinks(): Set<PersistentEntityId<*>> {
        val result = HashSet<PersistentEntityId<*>>()
        result.add(link)
        return result
    }

    override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
        index.index(this, link)
    }

    override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
        // TODO verify logic
        val mutablePreviousSet = HashSet(prev)
        val removedItem_link = mutablePreviousSet.remove(link)
        if (!removedItem_link) {
            index.index(this, link)
        }
        for (removed in mutablePreviousSet) {
            index.remove(this, removed)
        }
    }

    override fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean {
        var changed = false
        val link_data =         if (link == oldLink) {
            changed = true
            newLink as NameId
        }
        else {
            null
        }
        if (link_data != null) {
            link = link_data
        }
        return changed
    }

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<ComposedIdSoftRefEntity> {
        val modifiable = ComposedIdSoftRefEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): ComposedIdSoftRefEntity {
        val entity = ComposedIdSoftRefEntityImpl()
        entity._myName = myName
        entity._link = link
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun persistentId(): PersistentEntityId<*> {
        return ComposedId(myName, link)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ComposedIdSoftRefEntityData
        
        if (this.myName != other.myName) return false
        if (this.entitySource != other.entitySource) return false
        if (this.link != other.link) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ComposedIdSoftRefEntityData
        
        if (this.myName != other.myName) return false
        if (this.link != other.link) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + myName.hashCode()
        result = 31 * result + link.hashCode()
        return result
    }
}