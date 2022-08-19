package com.intellij.workspaceModel.storage.entities.test.api

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
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ParentMultipleEntityImpl : ParentMultipleEntity, WorkspaceEntityBase() {

  companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentMultipleEntity::class.java,
                                                                            ChildMultipleEntity::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_MANY, false)

    val connections = listOf<ConnectionId>(
      CHILDREN_CONNECTION_ID,
    )

  }

  @JvmField
  var _parentData: String? = null
  override val parentData: String
    get() = _parentData!!

  override val children: List<ChildMultipleEntity>
    get() = snapshot.extractOneToManyChildren<ChildMultipleEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: ParentMultipleEntityData?) : ModifiableWorkspaceEntityBase<ParentMultipleEntity>(), ParentMultipleEntity.Builder {
    constructor() : this(ParentMultipleEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ParentMultipleEntity is already created in a different builder")
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
      if (!getEntityData().isParentDataInitialized()) {
        error("Field ParentMultipleEntity#parentData should be initialized")
      }
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field ParentMultipleEntity#entitySource should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field ParentMultipleEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field ParentMultipleEntity#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ParentMultipleEntity
      this.parentData = dataSource.parentData
      this.entitySource = dataSource.entitySource
      if (parents != null) {
      }
    }


    override var parentData: String
      get() = getEntityData().parentData
      set(value) {
        checkModificationAllowed()
        getEntityData().parentData = value
        changedProperty.add("parentData")
      }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    // List of non-abstract referenced types
    var _children: List<ChildMultipleEntity>? = emptyList()
    override var children: List<ChildMultipleEntity>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyChildren<ChildMultipleEntity>(CHILDREN_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                                      CHILDREN_CONNECTION_ID)] as? List<ChildMultipleEntity>
                                                                                                          ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<ChildMultipleEntity> ?: emptyList()
        }
      }
      set(value) {
        // Setter of the list of non-abstract referenced types
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*> && (item_value as? ModifiableWorkspaceEntityBase<*>)?.diff == null) {
              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*>) {
              item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] = value
        }
        changedProperty.add("children")
      }

    override fun getEntityData(): ParentMultipleEntityData = result ?: super.getEntityData() as ParentMultipleEntityData
    override fun getEntityClass(): Class<ParentMultipleEntity> = ParentMultipleEntity::class.java
  }
}

class ParentMultipleEntityData : WorkspaceEntityData<ParentMultipleEntity>() {
  lateinit var parentData: String

  fun isParentDataInitialized(): Boolean = ::parentData.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ParentMultipleEntity> {
    val modifiable = ParentMultipleEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ParentMultipleEntity {
    val entity = ParentMultipleEntityImpl()
    entity._parentData = parentData
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ParentMultipleEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return ParentMultipleEntity(parentData, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as ParentMultipleEntityData

    if (this.parentData != other.parentData) return false
    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as ParentMultipleEntityData

    if (this.parentData != other.parentData) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + parentData.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + parentData.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
