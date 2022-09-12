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
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class LinkedListEntityImpl(val dataSource: LinkedListEntityData) : LinkedListEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  override val myName: String
    get() = dataSource.myName

  override val next: LinkedListEntityId
    get() = dataSource.next

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: LinkedListEntityData?) : ModifiableWorkspaceEntityBase<LinkedListEntity>(), LinkedListEntity.Builder {
    constructor() : this(LinkedListEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity LinkedListEntity is already created in a different builder")
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
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isMyNameInitialized()) {
        error("Field LinkedListEntity#myName should be initialized")
      }
      if (!getEntityData().isNextInitialized()) {
        error("Field LinkedListEntity#next should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as LinkedListEntity
      this.entitySource = dataSource.entitySource
      this.myName = dataSource.myName
      this.next = dataSource.next
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

    override var myName: String
      get() = getEntityData().myName
      set(value) {
        checkModificationAllowed()
        getEntityData().myName = value
        changedProperty.add("myName")
      }

    override var next: LinkedListEntityId
      get() = getEntityData().next
      set(value) {
        checkModificationAllowed()
        getEntityData().next = value
        changedProperty.add("next")

      }

    override fun getEntityData(): LinkedListEntityData = result ?: super.getEntityData() as LinkedListEntityData
    override fun getEntityClass(): Class<LinkedListEntity> = LinkedListEntity::class.java
  }
}

class LinkedListEntityData : WorkspaceEntityData.WithCalculablePersistentId<LinkedListEntity>(), SoftLinkable {
  lateinit var myName: String
  lateinit var next: LinkedListEntityId

  fun isMyNameInitialized(): Boolean = ::myName.isInitialized
  fun isNextInitialized(): Boolean = ::next.isInitialized

  override fun getLinks(): Set<PersistentEntityId<*>> {
    val result = HashSet<PersistentEntityId<*>>()
    result.add(next)
    return result
  }

  override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    index.index(this, next)
  }

  override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    val removedItem_next = mutablePreviousSet.remove(next)
    if (!removedItem_next) {
      index.index(this, next)
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean {
    var changed = false
    val next_data = if (next == oldLink) {
      changed = true
      newLink as LinkedListEntityId
    }
    else {
      null
    }
    if (next_data != null) {
      next = next_data
    }
    return changed
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<LinkedListEntity> {
    val modifiable = LinkedListEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): LinkedListEntity {
    return getCached(snapshot) {
      val entity = LinkedListEntityImpl(this)
      entity.entitySource = entitySource
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun persistentId(): PersistentEntityId<*> {
    return LinkedListEntityId(myName)
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return LinkedListEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return LinkedListEntity(myName, next, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as LinkedListEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.myName != other.myName) return false
    if (this.next != other.next) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as LinkedListEntityData

    if (this.myName != other.myName) return false
    if (this.next != other.next) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + myName.hashCode()
    result = 31 * result + next.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + myName.hashCode()
    result = 31 * result + next.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.add(LinkedListEntityId::class.java)
    collector.sameForAllEntities = true
  }
}
