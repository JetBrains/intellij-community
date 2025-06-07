// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.extractOneToManyParent
import com.intellij.platform.workspace.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.platform.workspace.storage.impl.updateOneToManyParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ContentRootEntityImpl(private val dataSource: ContentRootEntityData) : ContentRootEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleEntity::class.java, ContentRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val SOURCEROOTS_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ContentRootEntity::class.java, SourceRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val EXCLUDEDURLS_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ContentRootEntity::class.java, ExcludeUrlEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)

    private val connections = listOf<ConnectionId>(
      MODULE_CONNECTION_ID,
      SOURCEROOTS_CONNECTION_ID,
      EXCLUDEDURLS_CONNECTION_ID,
    )

  }

  override val url: VirtualFileUrl
    get() {
      readField("url")
      return dataSource.url
    }

  override val excludedPatterns: List<String>
    get() {
      readField("excludedPatterns")
      return dataSource.excludedPatterns
    }

  override val module: ModuleEntity
    get() = snapshot.extractOneToManyParent(MODULE_CONNECTION_ID, this)!!

  override val sourceRoots: List<SourceRootEntity>
    get() = snapshot.extractOneToManyChildren<SourceRootEntity>(SOURCEROOTS_CONNECTION_ID, this)!!.toList()

  override val excludedUrls: List<ExcludeUrlEntity>
    get() = snapshot.extractOneToManyChildren<ExcludeUrlEntity>(EXCLUDEDURLS_CONNECTION_ID, this)!!.toList()

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ContentRootEntityData?) : ModifiableWorkspaceEntityBase<ContentRootEntity, ContentRootEntityData>(result),
                                                           ContentRootEntity.Builder {
    internal constructor() : this(ContentRootEntityData())

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

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isUrlInitialized()) {
        error("Field ContentRootEntity#url should be initialized")
      }
      if (!getEntityData().isExcludedPatternsInitialized()) {
        error("Field ContentRootEntity#excludedPatterns should be initialized")
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
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

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

    override var module: ModuleEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(MODULE_CONNECTION_ID, this) as? ModuleEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity.Builder
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
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
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

    // List of non-abstract referenced types
    var _sourceRoots: List<SourceRootEntity>? = emptyList()
    override var sourceRoots: List<SourceRootEntity.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(SOURCEROOTS_CONNECTION_ID, this)!!
            .toList() as List<SourceRootEntity.Builder>) +
          (this.entityLinks[EntityLink(true, SOURCEROOTS_CONNECTION_ID)] as? List<SourceRootEntity.Builder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, SOURCEROOTS_CONNECTION_ID)] as? List<SourceRootEntity.Builder> ?: emptyList()
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

              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
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

    // List of non-abstract referenced types
    var _excludedUrls: List<ExcludeUrlEntity>? = emptyList()
    override var excludedUrls: List<ExcludeUrlEntity.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(EXCLUDEDURLS_CONNECTION_ID, this)!!
            .toList() as List<ExcludeUrlEntity.Builder>) +
          (this.entityLinks[EntityLink(true, EXCLUDEDURLS_CONNECTION_ID)] as? List<ExcludeUrlEntity.Builder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, EXCLUDEDURLS_CONNECTION_ID)] as? List<ExcludeUrlEntity.Builder> ?: emptyList()
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

              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
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

    override fun getEntityClass(): Class<ContentRootEntity> = ContentRootEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ContentRootEntityData : WorkspaceEntityData<ContentRootEntity>() {
  lateinit var url: VirtualFileUrl
  lateinit var excludedPatterns: MutableList<String>

  internal fun isUrlInitialized(): Boolean = ::url.isInitialized
  internal fun isExcludedPatternsInitialized(): Boolean = ::excludedPatterns.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ContentRootEntity> {
    val modifiable = ContentRootEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ContentRootEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ContentRootEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.jps.entities.ContentRootEntity") as EntityMetadata
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

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ContentRootEntity(url, excludedPatterns, entitySource) {
      parents.filterIsInstance<ModuleEntity.Builder>().singleOrNull()?.let { this.module = it }
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
}
