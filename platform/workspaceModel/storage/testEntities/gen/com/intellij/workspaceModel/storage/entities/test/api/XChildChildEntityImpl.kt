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
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class XChildChildEntityImpl(val dataSource: XChildChildEntityData) : XChildChildEntity, WorkspaceEntityBase() {

  companion object {
    internal val PARENT1_CONNECTION_ID: ConnectionId = ConnectionId.create(XParentEntity::class.java, XChildChildEntity::class.java,
                                                                           ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val PARENT2_CONNECTION_ID: ConnectionId = ConnectionId.create(XChildEntity::class.java, XChildChildEntity::class.java,
                                                                           ConnectionId.ConnectionType.ONE_TO_MANY, false)

    val connections = listOf<ConnectionId>(
      PARENT1_CONNECTION_ID,
      PARENT2_CONNECTION_ID,
    )

  }

  override val parent1: XParentEntity
    get() = snapshot.extractOneToManyParent(PARENT1_CONNECTION_ID, this)!!

  override val parent2: XChildEntity
    get() = snapshot.extractOneToManyParent(PARENT2_CONNECTION_ID, this)!!

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(var result: XChildChildEntityData?) : ModifiableWorkspaceEntityBase<XChildChildEntity>(), XChildChildEntity.Builder {
    constructor() : this(XChildChildEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity XChildChildEntity is already created in a different builder")
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
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(PARENT1_CONNECTION_ID, this) == null) {
          error("Field XChildChildEntity#parent1 should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT1_CONNECTION_ID)] == null) {
          error("Field XChildChildEntity#parent1 should be initialized")
        }
      }
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(PARENT2_CONNECTION_ID, this) == null) {
          error("Field XChildChildEntity#parent2 should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT2_CONNECTION_ID)] == null) {
          error("Field XChildChildEntity#parent2 should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as XChildChildEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (parents != null) {
        val parent1New = parents.filterIsInstance<XParentEntity>().single()
        if ((this.parent1 as WorkspaceEntityBase).id != (parent1New as WorkspaceEntityBase).id) {
          this.parent1 = parent1New
        }
        val parent2New = parents.filterIsInstance<XChildEntity>().single()
        if ((this.parent2 as WorkspaceEntityBase).id != (parent2New as WorkspaceEntityBase).id) {
          this.parent2 = parent2New
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

    override var parent1: XParentEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyParent(PARENT1_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                   PARENT1_CONNECTION_ID)]!! as XParentEntity
        }
        else {
          this.entityLinks[EntityLink(false, PARENT1_CONNECTION_ID)]!! as XParentEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, PARENT1_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENT1_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(PARENT1_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, PARENT1_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENT1_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENT1_CONNECTION_ID)] = value
        }
        changedProperty.add("parent1")
      }

    override var parent2: XChildEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyParent(PARENT2_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                   PARENT2_CONNECTION_ID)]!! as XChildEntity
        }
        else {
          this.entityLinks[EntityLink(false, PARENT2_CONNECTION_ID)]!! as XChildEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, PARENT2_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENT2_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(PARENT2_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, PARENT2_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENT2_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENT2_CONNECTION_ID)] = value
        }
        changedProperty.add("parent2")
      }

    override fun getEntityData(): XChildChildEntityData = result ?: super.getEntityData() as XChildChildEntityData
    override fun getEntityClass(): Class<XChildChildEntity> = XChildChildEntity::class.java
  }
}

class XChildChildEntityData : WorkspaceEntityData<XChildChildEntity>() {


  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<XChildChildEntity> {
    val modifiable = XChildChildEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): XChildChildEntity {
    return getCached(snapshot) {
      val entity = XChildChildEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return XChildChildEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return XChildChildEntity(entitySource) {
      this.parent1 = parents.filterIsInstance<XParentEntity>().single()
      this.parent2 = parents.filterIsInstance<XChildEntity>().single()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(XParentEntity::class.java)
    res.add(XChildEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as XChildChildEntityData

    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as XChildChildEntityData

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
