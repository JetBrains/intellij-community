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
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.SoftLinkable
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class WithSoftLinkEntityImpl(val dataSource: WithSoftLinkEntityData) : WithSoftLinkEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  override val link: NameId
    get() = dataSource.link

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(var result: WithSoftLinkEntityData?) : ModifiableWorkspaceEntityBase<WithSoftLinkEntity>(), WithSoftLinkEntity.Builder {
    constructor() : this(WithSoftLinkEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity WithSoftLinkEntity is already created in a different builder")
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
      if (!getEntityData().isLinkInitialized()) {
        error("Field WithSoftLinkEntity#link should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as WithSoftLinkEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.link != dataSource.link) this.link = dataSource.link
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

    override var link: NameId
      get() = getEntityData().link
      set(value) {
        checkModificationAllowed()
        getEntityData().link = value
        changedProperty.add("link")

      }

    override fun getEntityData(): WithSoftLinkEntityData = result ?: super.getEntityData() as WithSoftLinkEntityData
    override fun getEntityClass(): Class<WithSoftLinkEntity> = WithSoftLinkEntity::class.java
  }
}

class WithSoftLinkEntityData : WorkspaceEntityData<WithSoftLinkEntity>(), SoftLinkable {
  lateinit var link: NameId

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
    val link_data = if (link == oldLink) {
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

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<WithSoftLinkEntity> {
    val modifiable = WithSoftLinkEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): WithSoftLinkEntity {
    return getCached(snapshot) {
      val entity = WithSoftLinkEntityImpl(this)
      entity.entitySource = entitySource
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return WithSoftLinkEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return WithSoftLinkEntity(link, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as WithSoftLinkEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.link != other.link) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as WithSoftLinkEntityData

    if (this.link != other.link) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + link.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + link.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.add(NameId::class.java)
    collector.sameForAllEntities = true
  }
}
