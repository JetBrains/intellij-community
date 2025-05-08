// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildWithOptionalParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class XParentEntityImpl(private val dataSource: XParentEntityData) : XParentEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId =
      ConnectionId.create(XParentEntity::class.java, XChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val OPTIONALCHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(
      XParentEntity::class.java, XChildWithOptionalParentEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true
    )
    internal val CHILDCHILD_CONNECTION_ID: ConnectionId =
      ConnectionId.create(XParentEntity::class.java, XChildChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)

    private val connections = listOf<ConnectionId>(
      CHILDREN_CONNECTION_ID,
      OPTIONALCHILDREN_CONNECTION_ID,
      CHILDCHILD_CONNECTION_ID,
    )

  }

  override val parentProperty: String
    get() {
      readField("parentProperty")
      return dataSource.parentProperty
    }

  override val children: List<XChildEntity>
    get() = snapshot.extractOneToManyChildren<XChildEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()

  override val optionalChildren: List<XChildWithOptionalParentEntity>
    get() = snapshot.extractOneToManyChildren<XChildWithOptionalParentEntity>(OPTIONALCHILDREN_CONNECTION_ID, this)!!.toList()

  override val childChild: List<XChildChildEntity>
    get() = snapshot.extractOneToManyChildren<XChildChildEntity>(CHILDCHILD_CONNECTION_ID, this)!!.toList()

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: XParentEntityData?) : ModifiableWorkspaceEntityBase<XParentEntity, XParentEntityData>(result),
                                                       XParentEntity.Builder {
    internal constructor() : this(XParentEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity XParentEntity is already created in a different builder")
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
      if (!getEntityData().isParentPropertyInitialized()) {
        error("Field XParentEntity#parentProperty should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field XParentEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field XParentEntity#children should be initialized")
        }
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(OPTIONALCHILDREN_CONNECTION_ID, this) == null) {
          error("Field XParentEntity#optionalChildren should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, OPTIONALCHILDREN_CONNECTION_ID)] == null) {
          error("Field XParentEntity#optionalChildren should be initialized")
        }
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDCHILD_CONNECTION_ID, this) == null) {
          error("Field XParentEntity#childChild should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDCHILD_CONNECTION_ID)] == null) {
          error("Field XParentEntity#childChild should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as XParentEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.parentProperty != dataSource.parentProperty) this.parentProperty = dataSource.parentProperty
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var parentProperty: String
      get() = getEntityData().parentProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).parentProperty = value
        changedProperty.add("parentProperty")
      }

    // List of non-abstract referenced types
    var _children: List<XChildEntity>? = emptyList()
    override var children: List<XChildEntity.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this)!!
            .toList() as List<XChildEntity.Builder>) +
          (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<XChildEntity.Builder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<XChildEntity.Builder> ?: emptyList()
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

    // List of non-abstract referenced types
    var _optionalChildren: List<XChildWithOptionalParentEntity>? = emptyList()
    override var optionalChildren: List<XChildWithOptionalParentEntity.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(OPTIONALCHILDREN_CONNECTION_ID, this)!!
            .toList() as List<XChildWithOptionalParentEntity.Builder>) +
          (this.entityLinks[EntityLink(true, OPTIONALCHILDREN_CONNECTION_ID)] as? List<XChildWithOptionalParentEntity.Builder>
           ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, OPTIONALCHILDREN_CONNECTION_ID)] as? List<XChildWithOptionalParentEntity.Builder> ?: emptyList()
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
                item_value.entityLinks[EntityLink(false, OPTIONALCHILDREN_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.updateOneToManyChildrenOfParent(OPTIONALCHILDREN_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, OPTIONALCHILDREN_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, OPTIONALCHILDREN_CONNECTION_ID)] = value
        }
        changedProperty.add("optionalChildren")
      }

    // List of non-abstract referenced types
    var _childChild: List<XChildChildEntity>? = emptyList()
    override var childChild: List<XChildChildEntity.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDCHILD_CONNECTION_ID, this)!!
            .toList() as List<XChildChildEntity.Builder>) +
          (this.entityLinks[EntityLink(true, CHILDCHILD_CONNECTION_ID)] as? List<XChildChildEntity.Builder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDCHILD_CONNECTION_ID)] as? List<XChildChildEntity.Builder> ?: emptyList()
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
                item_value.entityLinks[EntityLink(false, CHILDCHILD_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.updateOneToManyChildrenOfParent(CHILDCHILD_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CHILDCHILD_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, CHILDCHILD_CONNECTION_ID)] = value
        }
        changedProperty.add("childChild")
      }

    override fun getEntityClass(): Class<XParentEntity> = XParentEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class XParentEntityData : WorkspaceEntityData<XParentEntity>() {
  lateinit var parentProperty: String

  internal fun isParentPropertyInitialized(): Boolean = ::parentProperty.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<XParentEntity> {
    val modifiable = XParentEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): XParentEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = XParentEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return XParentEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return XParentEntity(parentProperty, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as XParentEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.parentProperty != other.parentProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as XParentEntityData

    if (this.parentProperty != other.parentProperty) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + parentProperty.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + parentProperty.hashCode()
    return result
  }
}
