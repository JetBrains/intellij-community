// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.*
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
import com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SourceRootTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SourceRootTestOrderEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ContentRootTestEntityImpl(private val dataSource: ContentRootTestEntityData) : ContentRootTestEntity,
                                                                                              WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleTestEntity::class.java, ContentRootTestEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val SOURCEROOTORDER_CONNECTION_ID: ConnectionId = ConnectionId.create(
      ContentRootTestEntity::class.java, SourceRootTestOrderEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false
    )
    internal val SOURCEROOTS_CONNECTION_ID: ConnectionId = ConnectionId.create(
      ContentRootTestEntity::class.java, SourceRootTestEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false
    )

    private val connections = listOf<ConnectionId>(
      MODULE_CONNECTION_ID,
      SOURCEROOTORDER_CONNECTION_ID,
      SOURCEROOTS_CONNECTION_ID,
    )

  }

  override val module: ModuleTestEntity
    get() = snapshot.extractOneToManyParent(MODULE_CONNECTION_ID, this)!!

  override val sourceRootOrder: SourceRootTestOrderEntity?
    get() = snapshot.extractOneToOneChild(SOURCEROOTORDER_CONNECTION_ID, this)

  override val sourceRoots: List<SourceRootTestEntity>
    get() = snapshot.extractOneToManyChildren<SourceRootTestEntity>(SOURCEROOTS_CONNECTION_ID, this)!!.toList()

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ContentRootTestEntityData?) :
    ModifiableWorkspaceEntityBase<ContentRootTestEntity, ContentRootTestEntityData>(result), ContentRootTestEntity.Builder {
    internal constructor() : this(ContentRootTestEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ContentRootTestEntity is already created in a different builder")
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
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
          error("Field ContentRootTestEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field ContentRootTestEntity#module should be initialized")
        }
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(SOURCEROOTS_CONNECTION_ID, this) == null) {
          error("Field ContentRootTestEntity#sourceRoots should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, SOURCEROOTS_CONNECTION_ID)] == null) {
          error("Field ContentRootTestEntity#sourceRoots should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ContentRootTestEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var module: ModuleTestEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(MODULE_CONNECTION_ID, this) as? ModuleTestEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleTestEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleTestEntity.Builder
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

    override var sourceRootOrder: SourceRootTestOrderEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(
            SOURCEROOTORDER_CONNECTION_ID, this
          ) as? SourceRootTestOrderEntity.Builder)
          ?: (this.entityLinks[EntityLink(true, SOURCEROOTORDER_CONNECTION_ID)] as? SourceRootTestOrderEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(true, SOURCEROOTORDER_CONNECTION_ID)] as? SourceRootTestOrderEntity.Builder
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
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
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

    // List of non-abstract referenced types
    var _sourceRoots: List<SourceRootTestEntity>? = emptyList()
    override var sourceRoots: List<SourceRootTestEntity.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(SOURCEROOTS_CONNECTION_ID, this)!!
            .toList() as List<SourceRootTestEntity.Builder>) +
          (this.entityLinks[EntityLink(true, SOURCEROOTS_CONNECTION_ID)] as? List<SourceRootTestEntity.Builder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, SOURCEROOTS_CONNECTION_ID)] as? List<SourceRootTestEntity.Builder> ?: emptyList()
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

    override fun getEntityClass(): Class<ContentRootTestEntity> = ContentRootTestEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ContentRootTestEntityData : WorkspaceEntityData<ContentRootTestEntity>() {


  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ContentRootTestEntity> {
    val modifiable = ContentRootTestEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ContentRootTestEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ContentRootTestEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ContentRootTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ContentRootTestEntity(entitySource) {
      parents.filterIsInstance<ModuleTestEntity.Builder>().singleOrNull()?.let { this.module = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleTestEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ContentRootTestEntityData

    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ContentRootTestEntityData

    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    return result
  }
}
