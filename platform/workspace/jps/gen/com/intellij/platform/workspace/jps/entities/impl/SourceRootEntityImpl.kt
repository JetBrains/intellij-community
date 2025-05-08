// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToManyParent
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
internal class SourceRootEntityImpl(private val dataSource: SourceRootEntityData) : SourceRootEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val CONTENTROOT_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ContentRootEntity::class.java, SourceRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)

    private val connections = listOf<ConnectionId>(
      CONTENTROOT_CONNECTION_ID,
    )

  }

  override val url: VirtualFileUrl
    get() {
      readField("url")
      return dataSource.url
    }

  override val rootTypeId: SourceRootTypeId
    get() {
      readField("rootTypeId")
      return dataSource.rootTypeId
    }

  override val contentRoot: ContentRootEntity
    get() = snapshot.extractOneToManyParent(CONTENTROOT_CONNECTION_ID, this)!!

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: SourceRootEntityData?) : ModifiableWorkspaceEntityBase<SourceRootEntity, SourceRootEntityData>(result),
                                                          SourceRootEntity.Builder {
    internal constructor() : this(SourceRootEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SourceRootEntity is already created in a different builder")
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
        error("Field SourceRootEntity#url should be initialized")
      }
      if (!getEntityData().isRootTypeIdInitialized()) {
        error("Field SourceRootEntity#rootTypeId should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(CONTENTROOT_CONNECTION_ID, this) == null) {
          error("Field SourceRootEntity#contentRoot should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] == null) {
          error("Field SourceRootEntity#contentRoot should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SourceRootEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.url != dataSource.url) this.url = dataSource.url
      if (this.rootTypeId != dataSource.rootTypeId) this.rootTypeId = dataSource.rootTypeId
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

    override var rootTypeId: SourceRootTypeId
      get() = getEntityData().rootTypeId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).rootTypeId = value
        changedProperty.add("rootTypeId")

      }

    override var contentRoot: ContentRootEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(CONTENTROOT_CONNECTION_ID, this) as? ContentRootEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)]!! as ContentRootEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)]!! as ContentRootEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(CONTENTROOT_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] = value
        }
        changedProperty.add("contentRoot")
      }

    override fun getEntityClass(): Class<SourceRootEntity> = SourceRootEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SourceRootEntityData : WorkspaceEntityData<SourceRootEntity>() {
  lateinit var url: VirtualFileUrl
  lateinit var rootTypeId: SourceRootTypeId

  internal fun isUrlInitialized(): Boolean = ::url.isInitialized
  internal fun isRootTypeIdInitialized(): Boolean = ::rootTypeId.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SourceRootEntity> {
    val modifiable = SourceRootEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): SourceRootEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = SourceRootEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.jps.entities.SourceRootEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SourceRootEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return SourceRootEntity(url, rootTypeId, entitySource) {
      parents.filterIsInstance<ContentRootEntity.Builder>().singleOrNull()?.let { this.contentRoot = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ContentRootEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SourceRootEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.url != other.url) return false
    if (this.rootTypeId != other.rootTypeId) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SourceRootEntityData

    if (this.url != other.url) return false
    if (this.rootTypeId != other.rootTypeId) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + rootTypeId.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + rootTypeId.hashCode()
    return result
  }
}
