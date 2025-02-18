package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.workspaceModel.test.api.SealedClassWithLinks
import com.intellij.workspaceModel.test.api.SimpleId
import com.intellij.workspaceModel.test.api.SimpleSymbolicIdEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleSymbolicIdEntityImpl(private val dataSource: SimpleSymbolicIdEntityData) : SimpleSymbolicIdEntity,
                                                                                                WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val symbolicId: SimpleId = super.symbolicId

  override val version: Int
    get() {
      readField("version")
      return dataSource.version
    }
  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }

  override val related: SimpleId
    get() {
      readField("related")
      return dataSource.related
    }

  override val sealedClassWithLinks: SealedClassWithLinks
    get() {
      readField("sealedClassWithLinks")
      return dataSource.sealedClassWithLinks
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: SimpleSymbolicIdEntityData?) :
    ModifiableWorkspaceEntityBase<SimpleSymbolicIdEntity, SimpleSymbolicIdEntityData>(result), SimpleSymbolicIdEntity.Builder {
    internal constructor() : this(SimpleSymbolicIdEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SimpleSymbolicIdEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field SimpleSymbolicIdEntity#name should be initialized")
      }
      if (!getEntityData().isRelatedInitialized()) {
        error("Field SimpleSymbolicIdEntity#related should be initialized")
      }
      if (!getEntityData().isSealedClassWithLinksInitialized()) {
        error("Field SimpleSymbolicIdEntity#sealedClassWithLinks should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SimpleSymbolicIdEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.version != dataSource.version) this.version = dataSource.version
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.related != dataSource.related) this.related = dataSource.related
      if (this.sealedClassWithLinks != dataSource.sealedClassWithLinks) this.sealedClassWithLinks = dataSource.sealedClassWithLinks
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var version: Int
      get() = getEntityData().version
      set(value) {
        checkModificationAllowed()
        getEntityData(true).version = value
        changedProperty.add("version")
      }

    override var name: String
      get() = getEntityData().name
      set(value) {
        checkModificationAllowed()
        getEntityData(true).name = value
        changedProperty.add("name")
      }

    override var related: SimpleId
      get() = getEntityData().related
      set(value) {
        checkModificationAllowed()
        getEntityData(true).related = value
        changedProperty.add("related")

      }

    override var sealedClassWithLinks: SealedClassWithLinks
      get() = getEntityData().sealedClassWithLinks
      set(value) {
        checkModificationAllowed()
        getEntityData(true).sealedClassWithLinks = value
        changedProperty.add("sealedClassWithLinks")

      }

    override fun getEntityClass(): Class<SimpleSymbolicIdEntity> = SimpleSymbolicIdEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleSymbolicIdEntityData : WorkspaceEntityData<SimpleSymbolicIdEntity>(), SoftLinkable {
  var version: Int = 0
  lateinit var name: String
  lateinit var related: SimpleId
  lateinit var sealedClassWithLinks: SealedClassWithLinks


  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  internal fun isRelatedInitialized(): Boolean = ::related.isInitialized
  internal fun isSealedClassWithLinksInitialized(): Boolean = ::sealedClassWithLinks.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
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

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
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

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
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

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
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

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SimpleSymbolicIdEntity> {
    val modifiable = SimpleSymbolicIdEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): SimpleSymbolicIdEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = SimpleSymbolicIdEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.SimpleSymbolicIdEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SimpleSymbolicIdEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return SimpleSymbolicIdEntity(version, name, related, sealedClassWithLinks, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SimpleSymbolicIdEntityData

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

    other as SimpleSymbolicIdEntityData

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
}
