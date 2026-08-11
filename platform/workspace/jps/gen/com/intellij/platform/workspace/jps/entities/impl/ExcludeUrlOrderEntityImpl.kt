// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ContentRootEntityBuilder
import com.intellij.platform.workspace.jps.entities.ExcludeUrlOrderEntity
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ExcludeUrlOrderEntityImpl(private val dataSource: ExcludeUrlOrderEntityData) : ExcludeUrlOrderEntity,
                                                                                              WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CONTENTROOT_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ContentRootEntity::class.java, ExcludeUrlOrderEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val connections = listOf<ConnectionId>(CONTENTROOT_CONNECTION_ID)
  }

  override val order: List<VirtualFileUrl>
    get() {
      readField("order")
      return dataSource.order
    }
  override val contentRoot: ContentRootEntity
    get() = snapshot.instrumentation.getParent(CONTENTROOT_CONNECTION_ID, this) as? ContentRootEntity
            ?: error("Parent contentRoot not found for ExcludeUrlOrderEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ExcludeUrlOrderEntityData?) :
    ModifiableWorkspaceEntityBase<ExcludeUrlOrderEntity, ExcludeUrlOrderEntityData>(result), ExcludeUrlOrderEntity.Builder {
    internal constructor() : this(ExcludeUrlOrderEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isOrderInitialized()) {
        error("Field ExcludeUrlOrderEntity#order should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(CONTENTROOT_CONNECTION_ID, this) == null) {
          error("Field ExcludeUrlOrderEntity#contentRoot should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] == null) {
          error("Field ExcludeUrlOrderEntity#contentRoot should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_order = getEntityData().order
      if (collection_order is MutableWorkspaceList<*>) {
        collection_order.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ExcludeUrlOrderEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.order != dataSource.order) this.order = dataSource.order.toMutableList()
      updateChildToParentReferences(parents)
    }

    override fun index() {
      index(this, "order", this.order)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    private val orderUpdater: (value: List<VirtualFileUrl>) -> Unit = { value ->
      val _diff = diff
      if (_diff != null) index(this, "order", value)
      changedProperty.add("order")
    }
    override var order: MutableList<VirtualFileUrl>
      get() {
        val collection_order = getEntityData().order
        if (collection_order !is MutableWorkspaceList) return collection_order
        if (diff == null || modifiable.get()) {
          collection_order.setModificationUpdateAction(orderUpdater)
        }
        else {
          collection_order.cleanModificationUpdateAction()
        }
        return collection_order
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).order = value
        orderUpdater.invoke(value)
      }
    override var contentRoot: ContentRootEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(CONTENTROOT_CONNECTION_ID, this) as? ContentRootEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] as? ContentRootEntityBuilder)
          ?: error("contentRoot is null for ExcludeUrlOrderEntity")
        }
        else {
          (this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] as? ContentRootEntityBuilder)
          ?: error("contentRoot is null for ExcludeUrlOrderEntity")
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          value.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] = this
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(CONTENTROOT_CONNECTION_ID, value, this)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] = this
          }
          this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] = value
        }
        changedProperty.add("contentRoot")
      }

    override fun getEntityClass(): Class<ExcludeUrlOrderEntity> = ExcludeUrlOrderEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ExcludeUrlOrderEntityData : WorkspaceEntityData<ExcludeUrlOrderEntity>() {
  lateinit var order: MutableList<VirtualFileUrl>
  internal fun isOrderInitialized(): Boolean = ::order.isInitialized
  override fun newInstance(): ExcludeUrlOrderEntity = ExcludeUrlOrderEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ExcludeUrlOrderEntity, *> = ExcludeUrlOrderEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.jps.entities.ExcludeUrlOrderEntity") as EntityMetadata
  }

  override fun clone(): ExcludeUrlOrderEntityData {
    val clonedEntity = super.clone()
    clonedEntity as ExcludeUrlOrderEntityData
    clonedEntity.order = clonedEntity.order.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ExcludeUrlOrderEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ExcludeUrlOrderEntity(order, entitySource) {
      parents.filterIsInstance<ContentRootEntityBuilder>().singleOrNull()?.let { this.contentRoot = it }
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
    other as ExcludeUrlOrderEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.order != other.order) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ExcludeUrlOrderEntityData
    if (this.order != other.order) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + order.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + order.hashCode()
    return result
  }
}
