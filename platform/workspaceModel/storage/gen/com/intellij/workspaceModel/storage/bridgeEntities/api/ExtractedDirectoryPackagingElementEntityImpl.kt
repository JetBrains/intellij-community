// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyParentOfChild
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ExtractedDirectoryPackagingElementEntityImpl(val dataSource: ExtractedDirectoryPackagingElementEntityData) : ExtractedDirectoryPackagingElementEntity, WorkspaceEntityBase() {

  companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java,
                                                                                PackagingElementEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, true)

    val connections = listOf<ConnectionId>(
      PARENTENTITY_CONNECTION_ID,
    )

  }

  override val parentEntity: CompositePackagingElementEntity?
    get() = snapshot.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this)

  override val filePath: VirtualFileUrl
    get() = dataSource.filePath

  override val pathInArchive: String
    get() = dataSource.pathInArchive

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: ExtractedDirectoryPackagingElementEntityData?) : ModifiableWorkspaceEntityBase<ExtractedDirectoryPackagingElementEntity>(), ExtractedDirectoryPackagingElementEntity.Builder {
    constructor() : this(ExtractedDirectoryPackagingElementEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ExtractedDirectoryPackagingElementEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isFilePathInitialized()) {
        error("Field FileOrDirectoryPackagingElementEntity#filePath should be initialized")
      }
      if (!getEntityData().isPathInArchiveInitialized()) {
        error("Field ExtractedDirectoryPackagingElementEntity#pathInArchive should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ExtractedDirectoryPackagingElementEntity
      this.entitySource = dataSource.entitySource
      this.filePath = dataSource.filePath
      this.pathInArchive = dataSource.pathInArchive
      if (parents != null) {
        this.parentEntity = parents.filterIsInstance<CompositePackagingElementEntity>().singleOrNull()
      }
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override var parentEntity: CompositePackagingElementEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                                PARENTENTITY_CONNECTION_ID)] as? CompositePackagingElementEntity
        }
        else {
          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? CompositePackagingElementEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToAbstractManyParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("parentEntity")
      }

    override var filePath: VirtualFileUrl
      get() = getEntityData().filePath
      set(value) {
        checkModificationAllowed()
        getEntityData().filePath = value
        changedProperty.add("filePath")
        val _diff = diff
        if (_diff != null) index(this, "filePath", value)
      }

    override var pathInArchive: String
      get() = getEntityData().pathInArchive
      set(value) {
        checkModificationAllowed()
        getEntityData().pathInArchive = value
        changedProperty.add("pathInArchive")
      }

    override fun getEntityData(): ExtractedDirectoryPackagingElementEntityData = result
                                                                                 ?: super.getEntityData() as ExtractedDirectoryPackagingElementEntityData

    override fun getEntityClass(): Class<ExtractedDirectoryPackagingElementEntity> = ExtractedDirectoryPackagingElementEntity::class.java
  }
}

class ExtractedDirectoryPackagingElementEntityData : WorkspaceEntityData<ExtractedDirectoryPackagingElementEntity>() {
  lateinit var filePath: VirtualFileUrl
  lateinit var pathInArchive: String

  fun isFilePathInitialized(): Boolean = ::filePath.isInitialized
  fun isPathInArchiveInitialized(): Boolean = ::pathInArchive.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ExtractedDirectoryPackagingElementEntity> {
    val modifiable = ExtractedDirectoryPackagingElementEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ExtractedDirectoryPackagingElementEntity {
    return getCached(snapshot) {
      val entity = ExtractedDirectoryPackagingElementEntityImpl(this)
      entity.entitySource = entitySource
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ExtractedDirectoryPackagingElementEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return ExtractedDirectoryPackagingElementEntity(filePath, pathInArchive, entitySource) {
      this.parentEntity = parents.filterIsInstance<CompositePackagingElementEntity>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as ExtractedDirectoryPackagingElementEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.filePath != other.filePath) return false
    if (this.pathInArchive != other.pathInArchive) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as ExtractedDirectoryPackagingElementEntityData

    if (this.filePath != other.filePath) return false
    if (this.pathInArchive != other.pathInArchive) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + filePath.hashCode()
    result = 31 * result + pathInArchive.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + filePath.hashCode()
    result = 31 * result + pathInArchive.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    this.filePath?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
