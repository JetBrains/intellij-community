// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ProjectModelTestEntityImpl(val dataSource: ProjectModelTestEntityData) : ProjectModelTestEntity, WorkspaceEntityBase() {

  companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ProjectModelTestEntity::class.java,
                                                                                ProjectModelTestEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_MANY, true)
    internal val CHILDRENENTITIES_CONNECTION_ID: ConnectionId = ConnectionId.create(ProjectModelTestEntity::class.java,
                                                                                    ProjectModelTestEntity::class.java,
                                                                                    ConnectionId.ConnectionType.ONE_TO_MANY, true)
    internal val CONTENTROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(ProjectModelTestEntity::class.java,
                                                                               ContentRootEntity::class.java,
                                                                               ConnectionId.ConnectionType.ONE_TO_ONE, true)

    val connections = listOf<ConnectionId>(
      PARENTENTITY_CONNECTION_ID,
      CHILDRENENTITIES_CONNECTION_ID,
      CONTENTROOT_CONNECTION_ID,
    )

  }

  override val info: String
    get() = dataSource.info

  override val descriptor: Descriptor
    get() = dataSource.descriptor

  override val parentEntity: ProjectModelTestEntity?
    get() = snapshot.extractOneToManyParent(PARENTENTITY_CONNECTION_ID, this)

  override val childrenEntities: List<ProjectModelTestEntity>
    get() = snapshot.extractOneToManyChildren<ProjectModelTestEntity>(CHILDRENENTITIES_CONNECTION_ID, this)!!.toList()

  override val contentRoot: ContentRootEntity?
    get() = snapshot.extractOneToOneChild(CONTENTROOT_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: ProjectModelTestEntityData?) : ModifiableWorkspaceEntityBase<ProjectModelTestEntity, ProjectModelTestEntityData>(
    result), ProjectModelTestEntity.Builder {
    constructor() : this(ProjectModelTestEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ProjectModelTestEntity is already created in a different builder")
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
      if (!getEntityData().isInfoInitialized()) {
        error("Field ProjectModelTestEntity#info should be initialized")
      }
      if (!getEntityData().isDescriptorInitialized()) {
        error("Field ProjectModelTestEntity#descriptor should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDRENENTITIES_CONNECTION_ID, this) == null) {
          error("Field ProjectModelTestEntity#childrenEntities should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDRENENTITIES_CONNECTION_ID)] == null) {
          error("Field ProjectModelTestEntity#childrenEntities should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ProjectModelTestEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.info != dataSource.info) this.info = dataSource.info
      if (this.descriptor != dataSource.descriptor) this.descriptor = dataSource.descriptor
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var info: String
      get() = getEntityData().info
      set(value) {
        checkModificationAllowed()
        getEntityData(true).info = value
        changedProperty.add("info")
      }

    override var descriptor: Descriptor
      get() = getEntityData().descriptor
      set(value) {
        checkModificationAllowed()
        getEntityData(true).descriptor = value
        changedProperty.add("descriptor")

      }

    override var parentEntity: ProjectModelTestEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyParent(PARENTENTITY_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                        PARENTENTITY_CONNECTION_ID)] as? ProjectModelTestEntity
        }
        else {
          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? ProjectModelTestEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("parentEntity")
      }

    // List of non-abstract referenced types
    var _childrenEntities: List<ProjectModelTestEntity>? = emptyList()
    override var childrenEntities: List<ProjectModelTestEntity>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyChildren<ProjectModelTestEntity>(CHILDRENENTITIES_CONNECTION_ID,
                                                                 this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                 CHILDRENENTITIES_CONNECTION_ID)] as? List<ProjectModelTestEntity>
                                                                                     ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDRENENTITIES_CONNECTION_ID)] as? List<ProjectModelTestEntity> ?: emptyList()
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
                item_value.entityLinks[EntityLink(false, CHILDRENENTITIES_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToManyChildrenOfParent(CHILDRENENTITIES_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CHILDRENENTITIES_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, CHILDRENENTITIES_CONNECTION_ID)] = value
        }
        changedProperty.add("childrenEntities")
      }

    override var contentRoot: ContentRootEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(CONTENTROOT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true,
                                                                                                     CONTENTROOT_CONNECTION_ID)] as? ContentRootEntity
        }
        else {
          this.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] as? ContentRootEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(CONTENTROOT_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] = value
        }
        changedProperty.add("contentRoot")
      }

    override fun getEntityClass(): Class<ProjectModelTestEntity> = ProjectModelTestEntity::class.java
  }
}

class ProjectModelTestEntityData : WorkspaceEntityData<ProjectModelTestEntity>() {
  lateinit var info: String
  lateinit var descriptor: Descriptor

  fun isInfoInitialized(): Boolean = ::info.isInitialized
  fun isDescriptorInitialized(): Boolean = ::descriptor.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ProjectModelTestEntity> {
    val modifiable = ProjectModelTestEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ProjectModelTestEntity {
    return getCached(snapshot) {
      val entity = ProjectModelTestEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ProjectModelTestEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return ProjectModelTestEntity(info, descriptor, entitySource) {
      this.parentEntity = parents.filterIsInstance<ProjectModelTestEntity>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ProjectModelTestEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.info != other.info) return false
    if (this.descriptor != other.descriptor) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ProjectModelTestEntityData

    if (this.info != other.info) return false
    if (this.descriptor != other.descriptor) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + info.hashCode()
    result = 31 * result + descriptor.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + info.hashCode()
    result = 31 * result + descriptor.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    this.descriptor?.let { collector.addDataToInspect(it) }
    collector.sameForAllEntities = true
  }
}
