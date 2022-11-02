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
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyChildren
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyParent
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractOneParent
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyParentOfChild
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractOneParentOfChild
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class CompositeChildAbstractEntityImpl(val dataSource: CompositeChildAbstractEntityData) : CompositeChildAbstractEntity, WorkspaceEntityBase() {

  companion object {
    internal val PARENTINLIST_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositeAbstractEntity::class.java,
                                                                                SimpleAbstractEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositeAbstractEntity::class.java,
                                                                            SimpleAbstractEntity::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentChainEntity::class.java,
                                                                                CompositeAbstractEntity::class.java,
                                                                                ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, true)

    val connections = listOf<ConnectionId>(
      PARENTINLIST_CONNECTION_ID,
      CHILDREN_CONNECTION_ID,
      PARENTENTITY_CONNECTION_ID,
    )

  }

  override val parentInList: CompositeAbstractEntity
    get() = snapshot.extractOneToAbstractManyParent(PARENTINLIST_CONNECTION_ID, this)!!

  override val children: List<SimpleAbstractEntity>
    get() = snapshot.extractOneToAbstractManyChildren<SimpleAbstractEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()

  override val parentEntity: ParentChainEntity?
    get() = snapshot.extractOneToAbstractOneParent(PARENTENTITY_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: CompositeChildAbstractEntityData?) : ModifiableWorkspaceEntityBase<CompositeChildAbstractEntity, CompositeChildAbstractEntityData>(
    result), CompositeChildAbstractEntity.Builder {
    constructor() : this(CompositeChildAbstractEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity CompositeChildAbstractEntity is already created in a different builder")
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
      if (_diff != null) {
        if (_diff.extractOneToAbstractManyParent<WorkspaceEntityBase>(PARENTINLIST_CONNECTION_ID, this) == null) {
          error("Field SimpleAbstractEntity#parentInList should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENTINLIST_CONNECTION_ID)] == null) {
          error("Field SimpleAbstractEntity#parentInList should be initialized")
        }
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field CompositeAbstractEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field CompositeAbstractEntity#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as CompositeChildAbstractEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (parents != null) {
        val parentInListNew = parents.filterIsInstance<CompositeAbstractEntity>().single()
        if ((this.parentInList as WorkspaceEntityBase).id != (parentInListNew as WorkspaceEntityBase).id) {
          this.parentInList = parentInListNew
        }
        val parentEntityNew = parents.filterIsInstance<ParentChainEntity?>().singleOrNull()
        if ((parentEntityNew == null && this.parentEntity != null) || (parentEntityNew != null && this.parentEntity == null) || (parentEntityNew != null && this.parentEntity != null && (this.parentEntity as WorkspaceEntityBase).id != (parentEntityNew as WorkspaceEntityBase).id)) {
          this.parentEntity = parentEntityNew
        }
      }
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var parentInList: CompositeAbstractEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToAbstractManyParent(PARENTINLIST_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                                PARENTINLIST_CONNECTION_ID)]!! as CompositeAbstractEntity
        }
        else {
          this.entityLinks[EntityLink(false, PARENTINLIST_CONNECTION_ID)]!! as CompositeAbstractEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENTINLIST_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTINLIST_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToAbstractManyParentOfChild(PARENTINLIST_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENTINLIST_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTINLIST_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENTINLIST_CONNECTION_ID)] = value
        }
        changedProperty.add("parentInList")
      }

    override var children: List<SimpleAbstractEntity>
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToAbstractManyChildren<SimpleAbstractEntity>(CHILDREN_CONNECTION_ID,
                                                                       this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                       CHILDREN_CONNECTION_ID)] as? List<SimpleAbstractEntity>
                                                                                           ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as List<SimpleAbstractEntity> ?: emptyList()
        }
      }
      set(value) {
        // Set list of ref types for abstract entities
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
              // Backref setup before adding to store an abstract entity
              if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
                item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable
              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToAbstractManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value.asSequence())
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

    override var parentEntity: ParentChainEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToAbstractOneParent(PARENTENTITY_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                               PARENTENTITY_CONNECTION_ID)] as? ParentChainEntity
        }
        else {
          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? ParentChainEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToAbstractOneParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("parentEntity")
      }

    override fun getEntityClass(): Class<CompositeChildAbstractEntity> = CompositeChildAbstractEntity::class.java
  }
}

class CompositeChildAbstractEntityData : WorkspaceEntityData<CompositeChildAbstractEntity>() {


  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<CompositeChildAbstractEntity> {
    val modifiable = CompositeChildAbstractEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): CompositeChildAbstractEntity {
    return getCached(snapshot) {
      val entity = CompositeChildAbstractEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return CompositeChildAbstractEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return CompositeChildAbstractEntity(entitySource) {
      this.parentInList = parents.filterIsInstance<CompositeAbstractEntity>().single()
      this.parentEntity = parents.filterIsInstance<ParentChainEntity>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(CompositeAbstractEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as CompositeChildAbstractEntityData

    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as CompositeChildAbstractEntityData

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

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
