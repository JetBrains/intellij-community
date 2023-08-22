// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.UsedClassesCollector
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(2)
open class WithListSoftLinksEntityImpl(val dataSource: WithListSoftLinksEntityData) : WithListSoftLinksEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  override val myName: String
    get() = dataSource.myName

  override val links: List<NameId>
    get() = dataSource.links

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: WithListSoftLinksEntityData?) : ModifiableWorkspaceEntityBase<WithListSoftLinksEntity, WithListSoftLinksEntityData>(
    result), WithListSoftLinksEntity.Builder {
    constructor() : this(WithListSoftLinksEntityData())

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
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

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
        error("Field WithListSoftLinksEntity#myName should be initialized")
      }
      if (!getEntityData().isLinksInitialized()) {
        error("Field WithListSoftLinksEntity#links should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_links = getEntityData().links
      if (collection_links is MutableWorkspaceList<*>) {
        collection_links.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as WithListSoftLinksEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.myName != dataSource.myName) this.myName = dataSource.myName
      if (this.links != dataSource.links) this.links = dataSource.links.toMutableList()
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

    private val linksUpdater: (value: List<NameId>) -> Unit = { value ->

      changedProperty.add("links")
    }
    override var links: MutableList<NameId>
      get() {
        val collection_links = getEntityData().links
        if (collection_links !is MutableWorkspaceList) return collection_links
        if (diff == null || modifiable.get()) {
          collection_links.setModificationUpdateAction(linksUpdater)
        }
        else {
          collection_links.cleanModificationUpdateAction()
        }
        return collection_links
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).links = value
        linksUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<WithListSoftLinksEntity> = WithListSoftLinksEntity::class.java
  }
}

class WithListSoftLinksEntityData : WorkspaceEntityData.WithCalculableSymbolicId<WithListSoftLinksEntity>(), SoftLinkable {
  lateinit var myName: String
  lateinit var links: MutableList<NameId>

  fun isMyNameInitialized(): Boolean = ::myName.isInitialized
  fun isLinksInitialized(): Boolean = ::links.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    for (item in links) {
      result.add(item)
    }
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    for (item in links) {
      index.index(this, item)
    }
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
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

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val links_data = links.map {
      val it_data = if (it == oldLink) {
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
      links = links_data as MutableList<NameId>
    }
    return changed
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<WithListSoftLinksEntity> {
    val modifiable = WithListSoftLinksEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): WithListSoftLinksEntity {
    return getCached(snapshot) {
      val entity = WithListSoftLinksEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun clone(): WithListSoftLinksEntityData {
    val clonedEntity = super.clone()
    clonedEntity as WithListSoftLinksEntityData
    clonedEntity.links = clonedEntity.links.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun symbolicId(): SymbolicEntityId<*> {
    return AnotherNameId(myName)
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return WithListSoftLinksEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return WithListSoftLinksEntity(myName, links, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as WithListSoftLinksEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.myName != other.myName) return false
    if (this.links != other.links) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

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

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + myName.hashCode()
    result = 31 * result + links.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.add(NameId::class.java)
    this.links?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
