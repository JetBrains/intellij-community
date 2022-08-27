package com.intellij.workspaceModel.storage.entities.test.api

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
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class OoParentEntityImpl : OoParentEntity, WorkspaceEntityBase() {

  companion object {
    internal val CHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(OoParentEntity::class.java, OoChildEntity::class.java,
                                                                         ConnectionId.ConnectionType.ONE_TO_ONE, false)
    internal val ANOTHERCHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(OoParentEntity::class.java,
                                                                                OoChildWithNullableParentEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ONE, true)

    val connections = listOf<ConnectionId>(
      CHILD_CONNECTION_ID,
      ANOTHERCHILD_CONNECTION_ID,
    )

  }

  @JvmField
  var _parentProperty: String? = null
  override val parentProperty: String
    get() = _parentProperty!!

  override val child: OoChildEntity?
    get() = snapshot.extractOneToOneChild(CHILD_CONNECTION_ID, this)

  override val anotherChild: OoChildWithNullableParentEntity?
    get() = snapshot.extractOneToOneChild(ANOTHERCHILD_CONNECTION_ID, this)

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: OoParentEntityData?) : ModifiableWorkspaceEntityBase<OoParentEntity>(), OoParentEntity.Builder {
    constructor() : this(OoParentEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity OoParentEntity is already created in a different builder")
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
      if (!getEntityData().isParentPropertyInitialized()) {
        error("Field OoParentEntity#parentProperty should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as OoParentEntity
      this.entitySource = dataSource.entitySource
      this.parentProperty = dataSource.parentProperty
      if (parents != null) {
      }
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override var parentProperty: String
      get() = getEntityData().parentProperty
      set(value) {
        checkModificationAllowed()
        getEntityData().parentProperty = value
        changedProperty.add("parentProperty")
      }

    override var child: OoChildEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(CHILD_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] as? OoChildEntity
        }
        else {
          this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] as? OoChildEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(CHILD_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] = value
        }
        changedProperty.add("child")
      }

    override var anotherChild: OoChildWithNullableParentEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(ANOTHERCHILD_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true,
                                                                                                      ANOTHERCHILD_CONNECTION_ID)] as? OoChildWithNullableParentEntity
        }
        else {
          this.entityLinks[EntityLink(true, ANOTHERCHILD_CONNECTION_ID)] as? OoChildWithNullableParentEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(false, ANOTHERCHILD_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(ANOTHERCHILD_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(false, ANOTHERCHILD_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, ANOTHERCHILD_CONNECTION_ID)] = value
        }
        changedProperty.add("anotherChild")
      }

    override fun getEntityData(): OoParentEntityData = result ?: super.getEntityData() as OoParentEntityData
    override fun getEntityClass(): Class<OoParentEntity> = OoParentEntity::class.java
  }
}

class OoParentEntityData : WorkspaceEntityData<OoParentEntity>() {
  lateinit var parentProperty: String

  fun isParentPropertyInitialized(): Boolean = ::parentProperty.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<OoParentEntity> {
    val modifiable = OoParentEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): OoParentEntity {
    val entity = OoParentEntityImpl()
    entity._parentProperty = parentProperty
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return OoParentEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return OoParentEntity(parentProperty, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as OoParentEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.parentProperty != other.parentProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as OoParentEntityData

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

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
