// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.MutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ContentRootEntityImpl(val dataSource: ContentRootEntityData) : ContentRootEntity, WorkspaceEntityBase() {

  companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, ContentRootEntity::class.java,
                                                                          ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val EXCLUDEDURLS_CONNECTION_ID: ConnectionId = ConnectionId.create(ContentRootEntity::class.java, ExcludeUrlEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_MANY, true)
    internal val SOURCEROOTS_CONNECTION_ID: ConnectionId = ConnectionId.create(ContentRootEntity::class.java, SourceRootEntity::class.java,
                                                                               ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val SOURCEROOTORDER_CONNECTION_ID: ConnectionId = ConnectionId.create(ContentRootEntity::class.java,
                                                                                   SourceRootOrderEntity::class.java,
                                                                                   ConnectionId.ConnectionType.ONE_TO_ONE, false)

    val connections = listOf<ConnectionId>(
      MODULE_CONNECTION_ID,
      EXCLUDEDURLS_CONNECTION_ID,
      SOURCEROOTS_CONNECTION_ID,
      SOURCEROOTORDER_CONNECTION_ID,
    )

  }

  override val module: ModuleEntity
    get() = snapshot.extractOneToManyParent(MODULE_CONNECTION_ID, this)!!

  override val url: VirtualFileUrl
    get() = dataSource.url

  override val excludedUrls: List<ExcludeUrlEntity>
    get() = snapshot.extractOneToManyChildren<ExcludeUrlEntity>(EXCLUDEDURLS_CONNECTION_ID, this)!!.toList()

  override val excludedPatterns: List<String>
    get() = dataSource.excludedPatterns

  override val sourceRoots: List<SourceRootEntity>
    get() = snapshot.extractOneToManyChildren<SourceRootEntity>(SOURCEROOTS_CONNECTION_ID, this)!!.toList()

  override val sourceRootOrder: SourceRootOrderEntity?
    get() = snapshot.extractOneToOneChild(SOURCEROOTORDER_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: ContentRootEntityData?) : ModifiableWorkspaceEntityBase<ContentRootEntity, ContentRootEntityData>(
    result), ContentRootEntity.Builder {
    constructor() : this(ContentRootEntityData())

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
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
          error("Field ContentRootEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field ContentRootEntity#module should be initialized")
        }
      }
      if (!getEntityData().isUrlInitialized()) {
        error("Field ContentRootEntity#url should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(EXCLUDEDURLS_CONNECTION_ID, this) == null) {
          error("Field ContentRootEntity#excludedUrls should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, EXCLUDEDURLS_CONNECTION_ID)] == null) {
          error("Field ContentRootEntity#excludedUrls should be initialized")
        }
      }
      if (!getEntityData().isExcludedPatternsInitialized()) {
        error("Field ContentRootEntity#excludedPatterns should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(SOURCEROOTS_CONNECTION_ID, this) == null) {
          error("Field ContentRootEntity#sourceRoots should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, SOURCEROOTS_CONNECTION_ID)] == null) {
          error("Field ContentRootEntity#sourceRoots should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_excludedPatterns = getEntityData().excludedPatterns
      if (collection_excludedPatterns is MutableWorkspaceList<*>) {
        collection_excludedPatterns.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ContentRootEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.url != dataSource.url) this.url = dataSource.url
      if (this.excludedPatterns != dataSource.excludedPatterns) this.excludedPatterns = dataSource.excludedPatterns.toMutableList()
      if (parents != null) {
        val moduleNew = parents.filterIsInstance<ModuleEntity>().single()
        if ((this.module as WorkspaceEntityBase).id != (moduleNew as WorkspaceEntityBase).id) {
          this.module = moduleNew
        }
      }
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var module: ModuleEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyParent(MODULE_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                  MODULE_CONNECTION_ID)]!! as ModuleEntity
        }
        else {
          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(MODULE_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
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
    var _excludedUrls: List<ExcludeUrlEntity>? = emptyList()
    override var excludedUrls: List<ExcludeUrlEntity>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyChildren<ExcludeUrlEntity>(EXCLUDEDURLS_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                                       EXCLUDEDURLS_CONNECTION_ID)] as? List<ExcludeUrlEntity>
                                                                                                           ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, EXCLUDEDURLS_CONNECTION_ID)] as? List<ExcludeUrlEntity> ?: emptyList()
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
                item_value.entityLinks[EntityLink(false, EXCLUDEDURLS_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToManyChildrenOfParent(EXCLUDEDURLS_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, EXCLUDEDURLS_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, EXCLUDEDURLS_CONNECTION_ID)] = value
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
    var _sourceRoots: List<SourceRootEntity>? = emptyList()
    override var sourceRoots: List<SourceRootEntity>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyChildren<SourceRootEntity>(SOURCEROOTS_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                                      SOURCEROOTS_CONNECTION_ID)] as? List<SourceRootEntity>
                                                                                                          ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, SOURCEROOTS_CONNECTION_ID)] as? List<SourceRootEntity> ?: emptyList()
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
                item_value.entityLinks[EntityLink(false, SOURCEROOTS_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToManyChildrenOfParent(SOURCEROOTS_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, SOURCEROOTS_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, SOURCEROOTS_CONNECTION_ID)] = value
        }
        changedProperty.add("sourceRoots")
      }

    override var sourceRootOrder: SourceRootOrderEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(SOURCEROOTORDER_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true,
                                                                                                         SOURCEROOTORDER_CONNECTION_ID)] as? SourceRootOrderEntity
        }
        else {
          this.entityLinks[EntityLink(true, SOURCEROOTORDER_CONNECTION_ID)] as? SourceRootOrderEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, SOURCEROOTORDER_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(SOURCEROOTORDER_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, SOURCEROOTORDER_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, SOURCEROOTORDER_CONNECTION_ID)] = value
        }
        changedProperty.add("sourceRootOrder")
      }

    override fun getEntityClass(): Class<ContentRootEntity> = ContentRootEntity::class.java
  }
}

class ContentRootEntityData : WorkspaceEntityData<ContentRootEntity>() {
  lateinit var url: VirtualFileUrl
  lateinit var excludedPatterns: MutableList<String>

  fun isUrlInitialized(): Boolean = ::url.isInitialized
  fun isExcludedPatternsInitialized(): Boolean = ::excludedPatterns.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ContentRootEntity> {
    val modifiable = ContentRootEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ContentRootEntity {
    return getCached(snapshot) {
      val entity = ContentRootEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun clone(): ContentRootEntityData {
    val clonedEntity = super.clone()
    clonedEntity as ContentRootEntityData
    clonedEntity.excludedPatterns = clonedEntity.excludedPatterns.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ContentRootEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return ContentRootEntity(url, excludedPatterns, entitySource) {
      this.module = parents.filterIsInstance<ModuleEntity>().single()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ContentRootEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.url != other.url) return false
    if (this.excludedPatterns != other.excludedPatterns) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ContentRootEntityData

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

    other as ContentRootEntityData

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
