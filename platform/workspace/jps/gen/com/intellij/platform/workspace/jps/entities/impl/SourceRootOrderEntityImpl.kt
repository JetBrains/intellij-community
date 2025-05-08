// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("RootsExtensions")

package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootOrderEntity
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@Internal
@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SourceRootOrderEntityImpl(private val dataSource: SourceRootOrderEntityData) : SourceRootOrderEntity,
                                                                                              WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val CONTENTROOTENTITY_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ContentRootEntity::class.java, SourceRootOrderEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)

    private val connections = listOf<ConnectionId>(
      CONTENTROOTENTITY_CONNECTION_ID,
    )

  }

  override val orderOfSourceRoots: List<VirtualFileUrl>
    get() {
      readField("orderOfSourceRoots")
      return dataSource.orderOfSourceRoots
    }

  override val contentRootEntity: ContentRootEntity
    get() = snapshot.extractOneToOneParent(CONTENTROOTENTITY_CONNECTION_ID, this)!!

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: SourceRootOrderEntityData?) :
    ModifiableWorkspaceEntityBase<SourceRootOrderEntity, SourceRootOrderEntityData>(result), SourceRootOrderEntity.Builder {
    internal constructor() : this(SourceRootOrderEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SourceRootOrderEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "orderOfSourceRoots", this.orderOfSourceRoots)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isOrderOfSourceRootsInitialized()) {
        error("Field SourceRootOrderEntity#orderOfSourceRoots should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToOneParent<WorkspaceEntityBase>(CONTENTROOTENTITY_CONNECTION_ID, this) == null) {
          error("Field SourceRootOrderEntity#contentRootEntity should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, CONTENTROOTENTITY_CONNECTION_ID)] == null) {
          error("Field SourceRootOrderEntity#contentRootEntity should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_orderOfSourceRoots = getEntityData().orderOfSourceRoots
      if (collection_orderOfSourceRoots is MutableWorkspaceList<*>) {
        collection_orderOfSourceRoots.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SourceRootOrderEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.orderOfSourceRoots != dataSource.orderOfSourceRoots) this.orderOfSourceRoots = dataSource.orderOfSourceRoots.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    private val orderOfSourceRootsUpdater: (value: List<VirtualFileUrl>) -> Unit = { value ->
      val _diff = diff
      if (_diff != null) index(this, "orderOfSourceRoots", value)
      changedProperty.add("orderOfSourceRoots")
    }
    override var orderOfSourceRoots: MutableList<VirtualFileUrl>
      get() {
        val collection_orderOfSourceRoots = getEntityData().orderOfSourceRoots
        if (collection_orderOfSourceRoots !is MutableWorkspaceList) return collection_orderOfSourceRoots
        if (diff == null || modifiable.get()) {
          collection_orderOfSourceRoots.setModificationUpdateAction(orderOfSourceRootsUpdater)
        }
        else {
          collection_orderOfSourceRoots.cleanModificationUpdateAction()
        }
        return collection_orderOfSourceRoots
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).orderOfSourceRoots = value
        orderOfSourceRootsUpdater.invoke(value)
      }

    override var contentRootEntity: ContentRootEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(
            CONTENTROOTENTITY_CONNECTION_ID, this
          ) as? ContentRootEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, CONTENTROOTENTITY_CONNECTION_ID)]!! as ContentRootEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, CONTENTROOTENTITY_CONNECTION_ID)]!! as ContentRootEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, CONTENTROOTENTITY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(CONTENTROOTENTITY_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, CONTENTROOTENTITY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, CONTENTROOTENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("contentRootEntity")
      }

    override fun getEntityClass(): Class<SourceRootOrderEntity> = SourceRootOrderEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SourceRootOrderEntityData : WorkspaceEntityData<SourceRootOrderEntity>() {
  lateinit var orderOfSourceRoots: MutableList<VirtualFileUrl>

  internal fun isOrderOfSourceRootsInitialized(): Boolean = ::orderOfSourceRoots.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SourceRootOrderEntity> {
    val modifiable = SourceRootOrderEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): SourceRootOrderEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = SourceRootOrderEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.jps.entities.SourceRootOrderEntity") as EntityMetadata
  }

  override fun clone(): SourceRootOrderEntityData {
    val clonedEntity = super.clone()
    clonedEntity as SourceRootOrderEntityData
    clonedEntity.orderOfSourceRoots = clonedEntity.orderOfSourceRoots.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SourceRootOrderEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return SourceRootOrderEntity(orderOfSourceRoots, entitySource) {
      parents.filterIsInstance<ContentRootEntity.Builder>().singleOrNull()?.let { this.contentRootEntity = it }
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

    other as SourceRootOrderEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.orderOfSourceRoots != other.orderOfSourceRoots) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SourceRootOrderEntityData

    if (this.orderOfSourceRoots != other.orderOfSourceRoots) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + orderOfSourceRoots.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + orderOfSourceRoots.hashCode()
    return result
  }
}
