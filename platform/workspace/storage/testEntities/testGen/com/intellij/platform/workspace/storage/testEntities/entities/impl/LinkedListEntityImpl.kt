// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.LinkedListEntity
import com.intellij.platform.workspace.storage.testEntities.entities.LinkedListEntityId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class LinkedListEntityImpl(private val dataSource: LinkedListEntityData) : LinkedListEntity, WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val symbolicId: LinkedListEntityId = super.symbolicId

  override val myName: String
    get() {
      readField("myName")
      return dataSource.myName
    }

  override val next: LinkedListEntityId
    get() {
      readField("next")
      return dataSource.next
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: LinkedListEntityData?) : ModifiableWorkspaceEntityBase<LinkedListEntity, LinkedListEntityData>(result),
                                                          LinkedListEntity.Builder {
    internal constructor() : this(LinkedListEntityData())

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
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.myName != dataSource.myName) this.myName = dataSource.myName
      if (this.next != dataSource.next) this.next = dataSource.next
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var myName: String
      get() = getEntityData().myName
      set(value) {
        checkModificationAllowed()
        getEntityData(true).myName = value
        changedProperty.add("myName")
      }

    override var next: LinkedListEntityId
      get() = getEntityData().next
      set(value) {
        checkModificationAllowed()
        getEntityData(true).next = value
        changedProperty.add("next")

      }

    override fun getEntityClass(): Class<LinkedListEntity> = LinkedListEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class LinkedListEntityData : WorkspaceEntityData<LinkedListEntity>(), SoftLinkable {
  lateinit var myName: String
  lateinit var next: LinkedListEntityId

  internal fun isMyNameInitialized(): Boolean = ::myName.isInitialized
  internal fun isNextInitialized(): Boolean = ::next.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    result.add(next)
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    index.index(this, next)
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
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

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
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

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<LinkedListEntity> {
    val modifiable = LinkedListEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): LinkedListEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = LinkedListEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.LinkedListEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return LinkedListEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return LinkedListEntity(myName, next, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as LinkedListEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.myName != other.myName) return false
    if (this.next != other.next) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

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
}
