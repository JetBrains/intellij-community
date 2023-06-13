// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.UsedClassesCollector
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.extractOneToManyParent
import com.intellij.platform.workspace.storage.impl.extractOneToOneChild
import com.intellij.platform.workspace.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.platform.workspace.storage.impl.updateOneToManyParentOfChild
import com.intellij.platform.workspace.storage.impl.updateOneToOneChildOfParent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ContentRootEntityImpl(val dataSource: com.intellij.platform.workspace.jps.entities.ContentRootEntityData) : com.intellij.platform.workspace.jps.entities.ContentRootEntity, WorkspaceEntityBase() {

  companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(
      com.intellij.platform.workspace.jps.entities.ModuleEntity::class.java, com.intellij.platform.workspace.jps.entities.ContentRootEntity::class.java,
      ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val EXCLUDEDURLS_CONNECTION_ID: ConnectionId = ConnectionId.create(
      com.intellij.platform.workspace.jps.entities.ContentRootEntity::class.java, com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity::class.java,
      ConnectionId.ConnectionType.ONE_TO_MANY, true)
    internal val SOURCEROOTS_CONNECTION_ID: ConnectionId = ConnectionId.create(
      com.intellij.platform.workspace.jps.entities.ContentRootEntity::class.java, com.intellij.platform.workspace.jps.entities.SourceRootEntity::class.java,
      ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val SOURCEROOTORDER_CONNECTION_ID: ConnectionId = ConnectionId.create(
      com.intellij.platform.workspace.jps.entities.ContentRootEntity::class.java,
      com.intellij.platform.workspace.jps.entities.SourceRootOrderEntity::class.java,
      ConnectionId.ConnectionType.ONE_TO_ONE, false)
    internal val EXCLUDEURLORDER_CONNECTION_ID: ConnectionId = ConnectionId.create(
      com.intellij.platform.workspace.jps.entities.ContentRootEntity::class.java,
      com.intellij.platform.workspace.jps.entities.ExcludeUrlOrderEntity::class.java,
      ConnectionId.ConnectionType.ONE_TO_ONE, false)

    val connections = listOf<ConnectionId>(
      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.MODULE_CONNECTION_ID,
      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEDURLS_CONNECTION_ID,
      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTS_CONNECTION_ID,
      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTORDER_CONNECTION_ID,
      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEURLORDER_CONNECTION_ID,
    )

  }

  override val module: com.intellij.platform.workspace.jps.entities.ModuleEntity
    get() = snapshot.extractOneToManyParent(
      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.MODULE_CONNECTION_ID, this)!!

  override val url: VirtualFileUrl
    get() = dataSource.url

  override val excludedUrls: List<com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity>
    get() = snapshot.extractOneToManyChildren<com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity>(
      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEDURLS_CONNECTION_ID, this)!!.toList()

  override val excludedPatterns: List<String>
    get() = dataSource.excludedPatterns

  override val sourceRoots: List<com.intellij.platform.workspace.jps.entities.SourceRootEntity>
    get() = snapshot.extractOneToManyChildren<com.intellij.platform.workspace.jps.entities.SourceRootEntity>(
      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTS_CONNECTION_ID, this)!!.toList()

  override val sourceRootOrder: com.intellij.platform.workspace.jps.entities.SourceRootOrderEntity?
    get() = snapshot.extractOneToOneChild(
      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTORDER_CONNECTION_ID, this)

  override val excludeUrlOrder: com.intellij.platform.workspace.jps.entities.ExcludeUrlOrderEntity?
    get() = snapshot.extractOneToOneChild(
      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEURLORDER_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.connections
  }

  class Builder(result: com.intellij.platform.workspace.jps.entities.ContentRootEntityData?) : ModifiableWorkspaceEntityBase<com.intellij.platform.workspace.jps.entities.ContentRootEntity, com.intellij.platform.workspace.jps.entities.ContentRootEntityData>(
    result), com.intellij.platform.workspace.jps.entities.ContentRootEntity.Builder {
    constructor() : this(com.intellij.platform.workspace.jps.entities.ContentRootEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ContentRootEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "url", this.url)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(
            com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.MODULE_CONNECTION_ID, this) == null) {
          error("Field ContentRootEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false,
                                        com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.MODULE_CONNECTION_ID)] == null) {
          error("Field ContentRootEntity#module should be initialized")
        }
      }
      if (!getEntityData().isUrlInitialized()) {
        error("Field ContentRootEntity#url should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(
            com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEDURLS_CONNECTION_ID, this) == null) {
          error("Field ContentRootEntity#excludedUrls should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true,
                                        com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEDURLS_CONNECTION_ID)] == null) {
          error("Field ContentRootEntity#excludedUrls should be initialized")
        }
      }
      if (!getEntityData().isExcludedPatternsInitialized()) {
        error("Field ContentRootEntity#excludedPatterns should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(
            com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTS_CONNECTION_ID, this) == null) {
          error("Field ContentRootEntity#sourceRoots should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true,
                                        com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTS_CONNECTION_ID)] == null) {
          error("Field ContentRootEntity#sourceRoots should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.connections
    }

    override fun afterModification() {
      val collection_excludedPatterns = getEntityData().excludedPatterns
      if (collection_excludedPatterns is MutableWorkspaceList<*>) {
        collection_excludedPatterns.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as com.intellij.platform.workspace.jps.entities.ContentRootEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.url != dataSource.url) this.url = dataSource.url
      if (this.excludedPatterns != dataSource.excludedPatterns) this.excludedPatterns = dataSource.excludedPatterns.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var module: com.intellij.platform.workspace.jps.entities.ModuleEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyParent(
            com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.MODULE_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                                                                                       com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.MODULE_CONNECTION_ID)]!! as com.intellij.platform.workspace.jps.entities.ModuleEntity
        }
        else {
          this.entityLinks[EntityLink(false,
                                      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.MODULE_CONNECTION_ID)]!! as com.intellij.platform.workspace.jps.entities.ModuleEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true,
                                                     com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.MODULE_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true,
                                         com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.MODULE_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(
            com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.MODULE_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true,
                                                     com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.MODULE_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true,
                                         com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.MODULE_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false,
                                      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.MODULE_CONNECTION_ID)] = value
        }
        changedProperty.add("module")
      }

    override var url: VirtualFileUrl
      get() = getEntityData().url
      set(value) {
        checkModificationAllowed()
        getEntityData(true).url = value
        changedProperty.add("url")
        val _diff = diff
        if (_diff != null) index(this, "url", value)
      }

    // List of non-abstract referenced types
    var _excludedUrls: List<com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity>? = emptyList()
    override var excludedUrls: List<com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyChildren<com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity>(
            com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEDURLS_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                                                                                        com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEDURLS_CONNECTION_ID)] as? List<com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity>
                                                                                                                                                            ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true,
                                      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEDURLS_CONNECTION_ID)] as? List<com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity> ?: emptyList()
        }
      }
      set(value) {
        // Setter of the list of non-abstract referenced types
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
              // Backref setup before adding to store
              if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
                item_value.entityLinks[EntityLink(false,
                                                  com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEDURLS_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToManyChildrenOfParent(
            com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEDURLS_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false,
                                                com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEDURLS_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true,
                                      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEDURLS_CONNECTION_ID)] = value
        }
        changedProperty.add("excludedUrls")
      }

    private val excludedPatternsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("excludedPatterns")
    }
    override var excludedPatterns: MutableList<String>
      get() {
        val collection_excludedPatterns = getEntityData().excludedPatterns
        if (collection_excludedPatterns !is MutableWorkspaceList) return collection_excludedPatterns
        if (diff == null || modifiable.get()) {
          collection_excludedPatterns.setModificationUpdateAction(excludedPatternsUpdater)
        }
        else {
          collection_excludedPatterns.cleanModificationUpdateAction()
        }
        return collection_excludedPatterns
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).excludedPatterns = value
        excludedPatternsUpdater.invoke(value)
      }

    // List of non-abstract referenced types
    var _sourceRoots: List<com.intellij.platform.workspace.jps.entities.SourceRootEntity>? = emptyList()
    override var sourceRoots: List<com.intellij.platform.workspace.jps.entities.SourceRootEntity>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyChildren<com.intellij.platform.workspace.jps.entities.SourceRootEntity>(
            com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTS_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                                                                                       com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTS_CONNECTION_ID)] as? List<com.intellij.platform.workspace.jps.entities.SourceRootEntity>
                                                                                                                                                           ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true,
                                      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTS_CONNECTION_ID)] as? List<com.intellij.platform.workspace.jps.entities.SourceRootEntity> ?: emptyList()
        }
      }
      set(value) {
        // Setter of the list of non-abstract referenced types
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
              // Backref setup before adding to store
              if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
                item_value.entityLinks[EntityLink(false,
                                                  com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTS_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToManyChildrenOfParent(
            com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTS_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false,
                                                com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTS_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true,
                                      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTS_CONNECTION_ID)] = value
        }
        changedProperty.add("sourceRoots")
      }

    override var sourceRootOrder: com.intellij.platform.workspace.jps.entities.SourceRootOrderEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(
            com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTORDER_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true,
                                                                                                                                                                                com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTORDER_CONNECTION_ID)] as? com.intellij.platform.workspace.jps.entities.SourceRootOrderEntity
        }
        else {
          this.entityLinks[EntityLink(true,
                                      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTORDER_CONNECTION_ID)] as? com.intellij.platform.workspace.jps.entities.SourceRootOrderEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false,
                                         com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTORDER_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(
            com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTORDER_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false,
                                         com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTORDER_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true,
                                      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.SOURCEROOTORDER_CONNECTION_ID)] = value
        }
        changedProperty.add("sourceRootOrder")
      }

    override var excludeUrlOrder: com.intellij.platform.workspace.jps.entities.ExcludeUrlOrderEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(
            com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEURLORDER_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true,
                                                                                                                                                                                com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEURLORDER_CONNECTION_ID)] as? com.intellij.platform.workspace.jps.entities.ExcludeUrlOrderEntity
        }
        else {
          this.entityLinks[EntityLink(true,
                                      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEURLORDER_CONNECTION_ID)] as? com.intellij.platform.workspace.jps.entities.ExcludeUrlOrderEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false,
                                         com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEURLORDER_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(
            com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEURLORDER_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false,
                                         com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEURLORDER_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true,
                                      com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Companion.EXCLUDEURLORDER_CONNECTION_ID)] = value
        }
        changedProperty.add("excludeUrlOrder")
      }

    override fun getEntityClass(): Class<com.intellij.platform.workspace.jps.entities.ContentRootEntity> = com.intellij.platform.workspace.jps.entities.ContentRootEntity::class.java
  }
}

class ContentRootEntityData : WorkspaceEntityData<com.intellij.platform.workspace.jps.entities.ContentRootEntity>() {
  lateinit var url: VirtualFileUrl
  lateinit var excludedPatterns: MutableList<String>

  fun isUrlInitialized(): Boolean = ::url.isInitialized
  fun isExcludedPatternsInitialized(): Boolean = ::excludedPatterns.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<com.intellij.platform.workspace.jps.entities.ContentRootEntity> {
    val modifiable = com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): com.intellij.platform.workspace.jps.entities.ContentRootEntity {
    return getCached(snapshot) {
      val entity = com.intellij.platform.workspace.jps.entities.ContentRootEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun clone(): com.intellij.platform.workspace.jps.entities.ContentRootEntityData {
    val clonedEntity = super.clone()
    clonedEntity as com.intellij.platform.workspace.jps.entities.ContentRootEntityData
    clonedEntity.excludedPatterns = clonedEntity.excludedPatterns.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return com.intellij.platform.workspace.jps.entities.ContentRootEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return com.intellij.platform.workspace.jps.entities.ContentRootEntity(url, excludedPatterns, entitySource) {
      parents.filterIsInstance<com.intellij.platform.workspace.jps.entities.ModuleEntity>().singleOrNull()?.let { this.module = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(com.intellij.platform.workspace.jps.entities.ModuleEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as com.intellij.platform.workspace.jps.entities.ContentRootEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.url != other.url) return false
    if (this.excludedPatterns != other.excludedPatterns) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as com.intellij.platform.workspace.jps.entities.ContentRootEntityData

    if (this.url != other.url) return false
    if (this.excludedPatterns != other.excludedPatterns) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + excludedPatterns.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + excludedPatterns.hashCode()
    return result
  }

  override fun equalsByKey(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as com.intellij.platform.workspace.jps.entities.ContentRootEntityData

    if (this.url != other.url) return false
    return true
  }

  override fun hashCodeByKey(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + url.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    this.url?.let { collector.add(it::class.java) }
    this.excludedPatterns?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
