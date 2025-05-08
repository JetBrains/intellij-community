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
import com.intellij.platform.workspace.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.platform.workspace.storage.impl.updateOneToManyParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentLeafEntity
import com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentRootEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class TreeMultiparentLeafEntityImpl(private val dataSource: TreeMultiparentLeafEntityData) : TreeMultiparentLeafEntity,
                                                                                                      WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val MAINPARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(
      TreeMultiparentRootEntity::class.java, TreeMultiparentLeafEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true
    )
    internal val LEAFPARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(
      TreeMultiparentLeafEntity::class.java, TreeMultiparentLeafEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true
    )
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(
      TreeMultiparentLeafEntity::class.java, TreeMultiparentLeafEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true
    )

    private val connections = listOf<ConnectionId>(
      MAINPARENT_CONNECTION_ID,
      LEAFPARENT_CONNECTION_ID,
      CHILDREN_CONNECTION_ID,
    )

  }

  override val data: String
    get() {
      readField("data")
      return dataSource.data
    }

  override val mainParent: TreeMultiparentRootEntity?
    get() = snapshot.extractOneToManyParent(MAINPARENT_CONNECTION_ID, this)

  override val leafParent: TreeMultiparentLeafEntity?
    get() = snapshot.extractOneToManyParent(LEAFPARENT_CONNECTION_ID, this)

  override val children: List<TreeMultiparentLeafEntity>
    get() = snapshot.extractOneToManyChildren<TreeMultiparentLeafEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: TreeMultiparentLeafEntityData?) :
    ModifiableWorkspaceEntityBase<TreeMultiparentLeafEntity, TreeMultiparentLeafEntityData>(result), TreeMultiparentLeafEntity.Builder {
    internal constructor() : this(TreeMultiparentLeafEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity TreeMultiparentLeafEntity is already created in a different builder")
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
      if (!getEntityData().isDataInitialized()) {
        error("Field TreeMultiparentLeafEntity#data should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field TreeMultiparentLeafEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field TreeMultiparentLeafEntity#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as TreeMultiparentLeafEntity
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

    override var mainParent: TreeMultiparentRootEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(
            MAINPARENT_CONNECTION_ID, this
          ) as? TreeMultiparentRootEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, MAINPARENT_CONNECTION_ID)] as? TreeMultiparentRootEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, MAINPARENT_CONNECTION_ID)] as? TreeMultiparentRootEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, MAINPARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, MAINPARENT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(MAINPARENT_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, MAINPARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, MAINPARENT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, MAINPARENT_CONNECTION_ID)] = value
        }
        changedProperty.add("mainParent")
      }

    override var leafParent: TreeMultiparentLeafEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(
            LEAFPARENT_CONNECTION_ID, this
          ) as? TreeMultiparentLeafEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, LEAFPARENT_CONNECTION_ID)] as? TreeMultiparentLeafEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, LEAFPARENT_CONNECTION_ID)] as? TreeMultiparentLeafEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, LEAFPARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, LEAFPARENT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(LEAFPARENT_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, LEAFPARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, LEAFPARENT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, LEAFPARENT_CONNECTION_ID)] = value
        }
        changedProperty.add("leafParent")
      }

    // List of non-abstract referenced types
    var _children: List<TreeMultiparentLeafEntity>? = emptyList()
    override var children: List<TreeMultiparentLeafEntity.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this)!!
            .toList() as List<TreeMultiparentLeafEntity.Builder>) +
          (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<TreeMultiparentLeafEntity.Builder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<TreeMultiparentLeafEntity.Builder> ?: emptyList()
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
                item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.updateOneToManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] = value
        }
        changedProperty.add("children")
      }

    override fun getEntityClass(): Class<TreeMultiparentLeafEntity> = TreeMultiparentLeafEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class TreeMultiparentLeafEntityData : WorkspaceEntityData<TreeMultiparentLeafEntity>() {
  lateinit var data: String

  internal fun isDataInitialized(): Boolean = ::data.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<TreeMultiparentLeafEntity> {
    val modifiable = TreeMultiparentLeafEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): TreeMultiparentLeafEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = TreeMultiparentLeafEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentLeafEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return TreeMultiparentLeafEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return TreeMultiparentLeafEntity(data, entitySource) {
      this.mainParent = parents.filterIsInstance<TreeMultiparentRootEntity.Builder>().singleOrNull()
      this.leafParent = parents.filterIsInstance<TreeMultiparentLeafEntity.Builder>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as TreeMultiparentLeafEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as TreeMultiparentLeafEntityData

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
}
