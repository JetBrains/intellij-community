// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

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
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class SourceRootTestOrderEntityImpl(val dataSource: SourceRootTestOrderEntityData) : SourceRootTestOrderEntity, WorkspaceEntityBase() {

  companion object {
    internal val CONTENTROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(ContentRootTestEntity::class.java,
                                                                               SourceRootTestOrderEntity::class.java,
                                                                               ConnectionId.ConnectionType.ONE_TO_ONE, false)

    val connections = listOf<ConnectionId>(
      CONTENTROOT_CONNECTION_ID,
    )

  }

  override val data: String
    get() = dataSource.data

  override val contentRoot: ContentRootTestEntity
    get() = snapshot.extractOneToOneParent(CONTENTROOT_CONNECTION_ID, this)!!

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(var result: SourceRootTestOrderEntityData?) : ModifiableWorkspaceEntityBase<SourceRootTestOrderEntity>(), SourceRootTestOrderEntity.Builder {
    constructor() : this(SourceRootTestOrderEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SourceRootTestOrderEntity is already created in a different builder")
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
      if (!getEntityData().isDataInitialized()) {
        error("Field SourceRootTestOrderEntity#data should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToOneParent<WorkspaceEntityBase>(CONTENTROOT_CONNECTION_ID, this) == null) {
          error("Field SourceRootTestOrderEntity#contentRoot should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] == null) {
          error("Field SourceRootTestOrderEntity#contentRoot should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SourceRootTestOrderEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.data != dataSource.data) this.data = dataSource.data
      if (parents != null) {
        val contentRootNew = parents.filterIsInstance<ContentRootTestEntity>().single()
        if ((this.contentRoot as WorkspaceEntityBase).id != (contentRootNew as WorkspaceEntityBase).id) {
          this.contentRoot = contentRootNew
        }
      }
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override var data: String
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData().data = value
        changedProperty.add("data")
      }

    override var contentRoot: ContentRootTestEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneParent(CONTENTROOT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                      CONTENTROOT_CONNECTION_ID)]!! as ContentRootTestEntity
        }
        else {
          this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)]!! as ContentRootTestEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(CONTENTROOT_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] = value
        }
        changedProperty.add("contentRoot")
      }

    override fun getEntityData(): SourceRootTestOrderEntityData = result ?: super.getEntityData() as SourceRootTestOrderEntityData
    override fun getEntityClass(): Class<SourceRootTestOrderEntity> = SourceRootTestOrderEntity::class.java
  }
}

class SourceRootTestOrderEntityData : WorkspaceEntityData<SourceRootTestOrderEntity>() {
  lateinit var data: String

  fun isDataInitialized(): Boolean = ::data.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SourceRootTestOrderEntity> {
    val modifiable = SourceRootTestOrderEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): SourceRootTestOrderEntity {
    return getCached(snapshot) {
      val entity = SourceRootTestOrderEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SourceRootTestOrderEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return SourceRootTestOrderEntity(data, entitySource) {
      this.contentRoot = parents.filterIsInstance<ContentRootTestEntity>().single()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ContentRootTestEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SourceRootTestOrderEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SourceRootTestOrderEntityData

    if (this.data != other.data) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + data.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + data.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
