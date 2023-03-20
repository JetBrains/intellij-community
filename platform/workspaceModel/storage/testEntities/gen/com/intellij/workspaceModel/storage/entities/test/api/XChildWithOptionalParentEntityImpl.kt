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
open class XChildWithOptionalParentEntityImpl(val dataSource: XChildWithOptionalParentEntityData) : XChildWithOptionalParentEntity, WorkspaceEntityBase() {

  companion object {
    internal val OPTIONALPARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(XParentEntity::class.java,
                                                                                  XChildWithOptionalParentEntity::class.java,
                                                                                  ConnectionId.ConnectionType.ONE_TO_MANY, true)

    val connections = listOf<ConnectionId>(
      OPTIONALPARENT_CONNECTION_ID,
    )

  }

  override val childProperty: String
    get() = dataSource.childProperty

  override val optionalParent: XParentEntity?
    get() = snapshot.extractOneToManyParent(OPTIONALPARENT_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: XChildWithOptionalParentEntityData?) : ModifiableWorkspaceEntityBase<XChildWithOptionalParentEntity, XChildWithOptionalParentEntityData>(
    result), XChildWithOptionalParentEntity.Builder {
    constructor() : this(XChildWithOptionalParentEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity XChildWithOptionalParentEntity is already created in a different builder")
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
      if (!getEntityData().isChildPropertyInitialized()) {
        error("Field XChildWithOptionalParentEntity#childProperty should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as XChildWithOptionalParentEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.childProperty != dataSource.childProperty) this.childProperty = dataSource.childProperty
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var childProperty: String
      get() = getEntityData().childProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).childProperty = value
        changedProperty.add("childProperty")
      }

    override var optionalParent: XParentEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyParent(OPTIONALPARENT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                          OPTIONALPARENT_CONNECTION_ID)] as? XParentEntity
        }
        else {
          this.entityLinks[EntityLink(false, OPTIONALPARENT_CONNECTION_ID)] as? XParentEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, OPTIONALPARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, OPTIONALPARENT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(OPTIONALPARENT_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, OPTIONALPARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, OPTIONALPARENT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, OPTIONALPARENT_CONNECTION_ID)] = value
        }
        changedProperty.add("optionalParent")
      }

    override fun getEntityClass(): Class<XChildWithOptionalParentEntity> = XChildWithOptionalParentEntity::class.java
  }
}

class XChildWithOptionalParentEntityData : WorkspaceEntityData<XChildWithOptionalParentEntity>() {
  lateinit var childProperty: String

  fun isChildPropertyInitialized(): Boolean = ::childProperty.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<XChildWithOptionalParentEntity> {
    val modifiable = XChildWithOptionalParentEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): XChildWithOptionalParentEntity {
    return getCached(snapshot) {
      val entity = XChildWithOptionalParentEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return XChildWithOptionalParentEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return XChildWithOptionalParentEntity(childProperty, entitySource) {
      this.optionalParent = parents.filterIsInstance<XParentEntity>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as XChildWithOptionalParentEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.childProperty != other.childProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as XChildWithOptionalParentEntityData

    if (this.childProperty != other.childProperty) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + childProperty.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + childProperty.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
