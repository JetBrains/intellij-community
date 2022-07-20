package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.deft.api.annotations.Default
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
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.SoftLinkable
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class SimplePersistentIdEntityImpl: SimplePersistentIdEntity, WorkspaceEntityBase() {

    companion object {


        val connections = listOf<ConnectionId>(
        )

    }

    override var version: Int = 0
    @JvmField var _name: String? = null
    override val name: String
        get() = _name!!

    @JvmField var _related: SimpleId? = null
    override val related: SimpleId
        get() = _related!!

    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: SimplePersistentIdEntityData?): ModifiableWorkspaceEntityBase<SimplePersistentIdEntity>(), SimplePersistentIdEntity.Builder {
        constructor(): this(SimplePersistentIdEntityData())

        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity SimplePersistentIdEntity is already created in a different builder")
                }
            }

            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()

            // Process linked entities that are connected without a builder
            processLinkedEntities(builder)
            checkInitialization() // TODO uncomment and check failed tests
        }

        fun checkInitialization() {
            val _diff = diff
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field SimplePersistentIdEntity#entitySource should be initialized")
            }
            if (!getEntityData().isNameInitialized()) {
                error("Field SimplePersistentIdEntity#name should be initialized")
            }
            if (!getEntityData().isRelatedInitialized()) {
                error("Field SimplePersistentIdEntity#related should be initialized")
            }
        }

        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }


        override var version: Int
            get() = getEntityData().version
            set(value) {
                checkModificationAllowed()
                getEntityData().version = value
                changedProperty.add("version")
            }

        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")

            }

        override var name: String
            get() = getEntityData().name
            set(value) {
                checkModificationAllowed()
                getEntityData().name = value
                changedProperty.add("name")
            }

        override var related: SimpleId
            get() = getEntityData().related
            set(value) {
                checkModificationAllowed()
                getEntityData().related = value
                changedProperty.add("related")

            }

        override fun getEntityData(): SimplePersistentIdEntityData = result ?: super.getEntityData() as SimplePersistentIdEntityData
        override fun getEntityClass(): Class<SimplePersistentIdEntity> = SimplePersistentIdEntity::class.java
    }
}

class SimplePersistentIdEntityData : WorkspaceEntityData.WithCalculablePersistentId<SimplePersistentIdEntity>(), SoftLinkable {
    var version: Int = 0
    lateinit var name: String
    lateinit var related: SimpleId


    fun isNameInitialized(): Boolean = ::name.isInitialized
    fun isRelatedInitialized(): Boolean = ::related.isInitialized

    override fun getLinks(): Set<PersistentEntityId<*>> {
        val result = HashSet<PersistentEntityId<*>>()
        result.add(related)
        return result
    }

    override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
        index.index(this, related)
    }

    override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
        // TODO verify logic
        val mutablePreviousSet = HashSet(prev)
        val removedItem_related = mutablePreviousSet.remove(related)
        if (!removedItem_related) {
            index.index(this, related)
        }
        for (removed in mutablePreviousSet) {
            index.remove(this, removed)
        }
    }

    override fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean {
        var changed = false
        val related_data =         if (related == oldLink) {
            changed = true
            newLink as SimpleId
        }
        else {
            null
        }
        if (related_data != null) {
            related = related_data
        }
        return changed
    }

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<SimplePersistentIdEntity> {
        val modifiable = SimplePersistentIdEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): SimplePersistentIdEntity {
        val entity = SimplePersistentIdEntityImpl()
        entity.version = version
        entity._name = name
        entity._related = related
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun persistentId(): PersistentEntityId<*> {
        return SimpleId(name)
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return SimplePersistentIdEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false

        other as SimplePersistentIdEntityData

        if (this.version != other.version) return false
        if (this.entitySource != other.entitySource) return false
        if (this.name != other.name) return false
        if (this.related != other.related) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false

        other as SimplePersistentIdEntityData

        if (this.version != other.version) return false
        if (this.name != other.name) return false
        if (this.related != other.related) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + related.hashCode()
        return result
    }
}