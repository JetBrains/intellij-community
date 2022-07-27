//some copyright comment
package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class SimpleEntityImpl : SimpleEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  @JvmField
  var _name: String? = null
  override val name: String
    get() = _name!!

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: SimpleEntityData?) : ModifiableWorkspaceEntityBase<SimpleEntity>(), SimpleEntity.Builder {
    constructor() : this(SimpleEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SimpleEntity is already created in a different builder")
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
      if (!getEntityData().isNameInitialized()) {
        error("Field SimpleEntity#name should be initialized")
      }
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field SimpleEntity#entitySource should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }


    override var name: String
      get() = getEntityData().name
      set(value) {
        checkModificationAllowed()
        getEntityData().name = value
        changedProperty.add("name")
      }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override fun getEntityData(): SimpleEntityData = result ?: super.getEntityData() as SimpleEntityData
    override fun getEntityClass(): Class<SimpleEntity> = SimpleEntity::class.java
  }
}

class SimpleEntityData : WorkspaceEntityData<SimpleEntity>() {
  lateinit var name: String

  fun isNameInitialized(): Boolean = ::name.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<SimpleEntity> {
    val modifiable = SimpleEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): SimpleEntity {
    val entity = SimpleEntityImpl()
    entity._name = name
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SimpleEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as SimpleEntityData

    if (this.name != other.name) return false
    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as SimpleEntityData

    if (this.name != other.name) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    return result
  }
}
