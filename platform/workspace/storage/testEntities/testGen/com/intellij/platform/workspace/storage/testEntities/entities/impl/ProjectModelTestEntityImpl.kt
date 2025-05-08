// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

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
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.extractOneToManyParent
import com.intellij.platform.workspace.storage.impl.extractOneToOneChild
import com.intellij.platform.workspace.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.platform.workspace.storage.impl.updateOneToManyParentOfChild
import com.intellij.platform.workspace.storage.impl.updateOneToOneChildOfParent
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.Descriptor
import com.intellij.platform.workspace.storage.testEntities.entities.ProjectModelTestEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ProjectModelTestEntityImpl(private val dataSource: ProjectModelTestEntityData) : ProjectModelTestEntity,
                                                                                                WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(
      ProjectModelTestEntity::class.java, ProjectModelTestEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true
    )
    internal val CHILDRENENTITIES_CONNECTION_ID: ConnectionId = ConnectionId.create(
      ProjectModelTestEntity::class.java, ProjectModelTestEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true
    )
    internal val CONTENTROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(
      ProjectModelTestEntity::class.java, ContentRootTestEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, true
    )

    private val connections = listOf<ConnectionId>(
      PARENTENTITY_CONNECTION_ID,
      CHILDRENENTITIES_CONNECTION_ID,
      CONTENTROOT_CONNECTION_ID,
    )

  }

  override val info: String
    get() {
      readField("info")
      return dataSource.info
    }

  override val descriptor: Descriptor
    get() {
      readField("descriptor")
      return dataSource.descriptor
    }

  override val parentEntity: ProjectModelTestEntity?
    get() = snapshot.extractOneToManyParent(PARENTENTITY_CONNECTION_ID, this)

  override val childrenEntities: List<ProjectModelTestEntity>
    get() = snapshot.extractOneToManyChildren<ProjectModelTestEntity>(CHILDRENENTITIES_CONNECTION_ID, this)!!.toList()

  override val contentRoot: ContentRootTestEntity?
    get() = snapshot.extractOneToOneChild(CONTENTROOT_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ProjectModelTestEntityData?) :
    ModifiableWorkspaceEntityBase<ProjectModelTestEntity, ProjectModelTestEntityData>(result), ProjectModelTestEntity.Builder {
    internal constructor() : this(ProjectModelTestEntityData())

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
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
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

    override var parentEntity: ProjectModelTestEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(
            PARENTENTITY_CONNECTION_ID, this
          ) as? ProjectModelTestEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? ProjectModelTestEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? ProjectModelTestEntity.Builder
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
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
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
    override var childrenEntities: List<ProjectModelTestEntity.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDRENENTITIES_CONNECTION_ID, this)!!
            .toList() as List<ProjectModelTestEntity.Builder>) +
          (this.entityLinks[EntityLink(true, CHILDRENENTITIES_CONNECTION_ID)] as? List<ProjectModelTestEntity.Builder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDRENENTITIES_CONNECTION_ID)] as? List<ProjectModelTestEntity.Builder> ?: emptyList()
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

              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
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

    override var contentRoot: ContentRootTestEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(
            CONTENTROOT_CONNECTION_ID, this
          ) as? ContentRootTestEntity.Builder)
          ?: (this.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] as? ContentRootTestEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] as? ContentRootTestEntity.Builder
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
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
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

@OptIn(WorkspaceEntityInternalApi::class)
internal class ProjectModelTestEntityData : WorkspaceEntityData<ProjectModelTestEntity>() {
  lateinit var info: String
  lateinit var descriptor: Descriptor

  internal fun isInfoInitialized(): Boolean = ::info.isInitialized
  internal fun isDescriptorInitialized(): Boolean = ::descriptor.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ProjectModelTestEntity> {
    val modifiable = ProjectModelTestEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ProjectModelTestEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ProjectModelTestEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.ProjectModelTestEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ProjectModelTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ProjectModelTestEntity(info, descriptor, entitySource) {
      this.parentEntity = parents.filterIsInstance<ProjectModelTestEntity.Builder>().singleOrNull()
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
}
