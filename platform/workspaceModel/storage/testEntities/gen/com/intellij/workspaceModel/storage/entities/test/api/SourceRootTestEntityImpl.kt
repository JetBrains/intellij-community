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
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class SourceRootTestEntityImpl(val dataSource: SourceRootTestEntityData) : SourceRootTestEntity, WorkspaceEntityBase() {

  companion object {
    internal val CONTENTROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(ContentRootTestEntity::class.java,
                                                                               SourceRootTestEntity::class.java,
                                                                               ConnectionId.ConnectionType.ONE_TO_MANY, false)

    val connections = listOf<ConnectionId>(
      CONTENTROOT_CONNECTION_ID,
    )

  }

  override val data: String
    get() = dataSource.data

  override val contentRoot: ContentRootTestEntity
    get() = snapshot.extractOneToManyParent(CONTENTROOT_CONNECTION_ID, this)!!

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: SourceRootTestEntityData?) : ModifiableWorkspaceEntityBase<SourceRootTestEntity, SourceRootTestEntityData>(
    result), SourceRootTestEntity.Builder {
    constructor() : this(SourceRootTestEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SourceRootTestEntity is already created in a different builder")
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
      if (!getEntityData().isDataInitialized()) {
        error("Field SourceRootTestEntity#data should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(CONTENTROOT_CONNECTION_ID, this) == null) {
          error("Field SourceRootTestEntity#contentRoot should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] == null) {
          error("Field SourceRootTestEntity#contentRoot should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SourceRootTestEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.data != dataSource.data) this.data = dataSource.data
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var data: String
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData(true).data = value
        changedProperty.add("data")
      }

    override var contentRoot: ContentRootTestEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyParent(CONTENTROOT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                       CONTENTROOT_CONNECTION_ID)]!! as ContentRootTestEntity
        }
        else {
          this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)]!! as ContentRootTestEntity
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
          _diff.addEntity(value)
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

    override fun getEntityClass(): Class<SourceRootTestEntity> = SourceRootTestEntity::class.java
  }
}

class SourceRootTestEntityData : WorkspaceEntityData<SourceRootTestEntity>() {
  lateinit var data: String

  fun isDataInitialized(): Boolean = ::data.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SourceRootTestEntity> {
    val modifiable = SourceRootTestEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): SourceRootTestEntity {
    return getCached(snapshot) {
      val entity = SourceRootTestEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SourceRootTestEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return SourceRootTestEntity(data, entitySource) {
      parents.filterIsInstance<ContentRootTestEntity>().singleOrNull()?.let { this.contentRoot = it }
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

    other as SourceRootTestEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SourceRootTestEntityData

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
