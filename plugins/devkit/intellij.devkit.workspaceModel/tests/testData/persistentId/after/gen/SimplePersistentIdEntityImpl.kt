package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.deft.api.annotations.Default
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.SoftLinkable
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class SimplePersistentIdEntityImpl(val dataSource: SimplePersistentIdEntityData) : SimplePersistentIdEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  override val version: Int get() = dataSource.version
  override val name: String
    get() = dataSource.name

  override val related: SimpleId
    get() = dataSource.related

  override val sealedClassWithLinks: SealedClassWithLinks
    get() = dataSource.sealedClassWithLinks

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(var result: SimplePersistentIdEntityData?) : ModifiableWorkspaceEntityBase<SimplePersistentIdEntity>(), SimplePersistentIdEntity.Builder {
    constructor() : this(SimplePersistentIdEntityData())

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
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.result = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field SimplePersistentIdEntity#name should be initialized")
      }
      if (!getEntityData().isRelatedInitialized()) {
        error("Field SimplePersistentIdEntity#related should be initialized")
      }
      if (!getEntityData().isSealedClassWithLinksInitialized()) {
        error("Field SimplePersistentIdEntity#sealedClassWithLinks should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SimplePersistentIdEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.version != dataSource.version) this.version = dataSource.version
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.related != dataSource.related) this.related = dataSource.related
      if (this.sealedClassWithLinks != dataSource.sealedClassWithLinks) this.sealedClassWithLinks = dataSource.sealedClassWithLinks
      if (parents != null) {
      }
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override var version: Int
      get() = getEntityData().version
      set(value) {
        checkModificationAllowed()
        getEntityData().version = value
        changedProperty.add("version")
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

    override var sealedClassWithLinks: SealedClassWithLinks
      get() = getEntityData().sealedClassWithLinks
      set(value) {
        checkModificationAllowed()
        getEntityData().sealedClassWithLinks = value
        changedProperty.add("sealedClassWithLinks")

      }

    override fun getEntityData(): SimplePersistentIdEntityData = result ?: super.getEntityData() as SimplePersistentIdEntityData
    override fun getEntityClass(): Class<SimplePersistentIdEntity> = SimplePersistentIdEntity::class.java
  }
}

class SimplePersistentIdEntityData : WorkspaceEntityData.WithCalculablePersistentId<SimplePersistentIdEntity>(), SoftLinkable {
  var version: Int = 0
  lateinit var name: String
  lateinit var related: SimpleId
  lateinit var sealedClassWithLinks: SealedClassWithLinks


  fun isNameInitialized(): Boolean = ::name.isInitialized
  fun isRelatedInitialized(): Boolean = ::related.isInitialized
  fun isSealedClassWithLinksInitialized(): Boolean = ::sealedClassWithLinks.isInitialized

  override fun getLinks(): Set<PersistentEntityId<*>> {
    val result = HashSet<PersistentEntityId<*>>()
    result.add(related)
    val _sealedClassWithLinks = sealedClassWithLinks
    when (_sealedClassWithLinks) {
      is SealedClassWithLinks.Many -> {
        val __sealedClassWithLinks = _sealedClassWithLinks
        when (__sealedClassWithLinks) {
          is SealedClassWithLinks.Many.Ordered -> {
            for (item in __sealedClassWithLinks.list) {
              result.add(item)
            }
          }
          is SealedClassWithLinks.Many.Unordered -> {
            for (item in __sealedClassWithLinks.set) {
              result.add(item)
            }
          }
        }
      }
      is SealedClassWithLinks.Nothing -> {
      }
      is SealedClassWithLinks.Single -> {
        result.add(_sealedClassWithLinks.id)
      }
    }
    return result
  }

  override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    index.index(this, related)
    val _sealedClassWithLinks = sealedClassWithLinks
    when (_sealedClassWithLinks) {
      is SealedClassWithLinks.Many -> {
        val __sealedClassWithLinks = _sealedClassWithLinks
        when (__sealedClassWithLinks) {
          is SealedClassWithLinks.Many.Ordered -> {
            for (item in __sealedClassWithLinks.list) {
              index.index(this, item)
            }
          }
          is SealedClassWithLinks.Many.Unordered -> {
            for (item in __sealedClassWithLinks.set) {
              index.index(this, item)
            }
          }
        }
      }
      is SealedClassWithLinks.Nothing -> {
      }
      is SealedClassWithLinks.Single -> {
        index.index(this, _sealedClassWithLinks.id)
      }
    }
  }

  override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    val removedItem_related = mutablePreviousSet.remove(related)
    if (!removedItem_related) {
      index.index(this, related)
    }
    val _sealedClassWithLinks = sealedClassWithLinks
    when (_sealedClassWithLinks) {
      is SealedClassWithLinks.Many -> {
        val __sealedClassWithLinks = _sealedClassWithLinks
        when (__sealedClassWithLinks) {
          is SealedClassWithLinks.Many.Ordered -> {
            for (item in __sealedClassWithLinks.list) {
              val removedItem_item = mutablePreviousSet.remove(item)
              if (!removedItem_item) {
                index.index(this, item)
              }
            }
          }
          is SealedClassWithLinks.Many.Unordered -> {
            for (item in __sealedClassWithLinks.set) {
              val removedItem_item = mutablePreviousSet.remove(item)
              if (!removedItem_item) {
                index.index(this, item)
              }
            }
          }
        }
      }
      is SealedClassWithLinks.Nothing -> {
      }
      is SealedClassWithLinks.Single -> {
        val removedItem__sealedClassWithLinks_id = mutablePreviousSet.remove(_sealedClassWithLinks.id)
        if (!removedItem__sealedClassWithLinks_id) {
          index.index(this, _sealedClassWithLinks.id)
        }
      }
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean {
    var changed = false
    val related_data = if (related == oldLink) {
      changed = true
      newLink as SimpleId
    }
    else {
      null
    }
    if (related_data != null) {
      related = related_data
    }
    val _sealedClassWithLinks = sealedClassWithLinks
    val res_sealedClassWithLinks = when (_sealedClassWithLinks) {
      is SealedClassWithLinks.Many -> {
        val __sealedClassWithLinks = _sealedClassWithLinks
        val res__sealedClassWithLinks = when (__sealedClassWithLinks) {
          is SealedClassWithLinks.Many.Ordered -> {
            val __sealedClassWithLinks_list_data = __sealedClassWithLinks.list.map {
              val it_data = if (it == oldLink) {
                changed = true
                newLink as SimpleId
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
            var __sealedClassWithLinks_data = __sealedClassWithLinks
            if (__sealedClassWithLinks_list_data != null) {
              __sealedClassWithLinks_data = __sealedClassWithLinks_data.copy(list = __sealedClassWithLinks_list_data)
            }
            __sealedClassWithLinks_data
          }
          is SealedClassWithLinks.Many.Unordered -> {
            val __sealedClassWithLinks_set_data = __sealedClassWithLinks.set.map {
              val it_data = if (it == oldLink) {
                changed = true
                newLink as SimpleId
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
            var __sealedClassWithLinks_data = __sealedClassWithLinks
            if (__sealedClassWithLinks_set_data != null) {
              __sealedClassWithLinks_data = __sealedClassWithLinks_data.copy(set = __sealedClassWithLinks_set_data)
            }
            __sealedClassWithLinks_data
          }
        }
        res__sealedClassWithLinks
      }
      is SealedClassWithLinks.Nothing -> {
        _sealedClassWithLinks
      }
      is SealedClassWithLinks.Single -> {
        val _sealedClassWithLinks_id_data = if (_sealedClassWithLinks.id == oldLink) {
          changed = true
          newLink as SimpleId
        }
        else {
          null
        }
        var _sealedClassWithLinks_data = _sealedClassWithLinks
        if (_sealedClassWithLinks_id_data != null) {
          _sealedClassWithLinks_data = _sealedClassWithLinks_data.copy(id = _sealedClassWithLinks_id_data)
        }
        _sealedClassWithLinks_data
      }
    }
    if (res_sealedClassWithLinks != null) {
      sealedClassWithLinks = res_sealedClassWithLinks
    }
    return changed
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SimplePersistentIdEntity> {
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
    return getCached(snapshot) {
      val entity = SimplePersistentIdEntityImpl(this)
      entity.entitySource = entitySource
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
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

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return SimplePersistentIdEntity(version, name, related, sealedClassWithLinks, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SimplePersistentIdEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.version != other.version) return false
    if (this.name != other.name) return false
    if (this.related != other.related) return false
    if (this.sealedClassWithLinks != other.sealedClassWithLinks) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SimplePersistentIdEntityData

    if (this.version != other.version) return false
    if (this.name != other.name) return false
    if (this.related != other.related) return false
    if (this.sealedClassWithLinks != other.sealedClassWithLinks) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + related.hashCode()
    result = 31 * result + sealedClassWithLinks.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + related.hashCode()
    result = 31 * result + sealedClassWithLinks.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.add(SimpleId::class.java)
    collector.add(SealedClassWithLinks.Many.Unordered::class.java)
    collector.add(SealedClassWithLinks.Many::class.java)
    collector.add(SealedClassWithLinks.Single::class.java)
    collector.add(SealedClassWithLinks::class.java)
    collector.add(SealedClassWithLinks.Many.Ordered::class.java)
    collector.addObject(SealedClassWithLinks.Nothing::class.java)
    this.sealedClassWithLinks?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = true
  }
}
