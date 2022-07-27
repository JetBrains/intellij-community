package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.deft.api.annotations.Default
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
open class DefaultFieldEntityImpl : DefaultFieldEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  override var version: Int = 0
  @JvmField
  var _data: TestData? = null
  override val data: TestData
    get() = _data!!

  override var anotherVersion: Int = super<DefaultFieldEntity>.anotherVersion

  override var description: String = super<DefaultFieldEntity>.description

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: DefaultFieldEntityData?) : ModifiableWorkspaceEntityBase<DefaultFieldEntity>(), DefaultFieldEntity.Builder {
    constructor() : this(DefaultFieldEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity DefaultFieldEntity is already created in a different builder")
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
        error("Field DefaultFieldEntity#entitySource should be initialized")
      }
      if (!getEntityData().isDataInitialized()) {
        error("Field DefaultFieldEntity#data should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }


    override var version: Int
      get() = getEntityData().version
      set(value) {
        checkModificationAllowed()
        getEntityData().version = value
        changedProperty.add("version")
      }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override var data: TestData
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData().data = value
        changedProperty.add("data")

      }

    override var anotherVersion: Int
      get() = getEntityData().anotherVersion
      set(value) {
        checkModificationAllowed()
        getEntityData().anotherVersion = value
        changedProperty.add("anotherVersion")
      }

    override var description: String
      get() = getEntityData().description
      set(value) {
        checkModificationAllowed()
        getEntityData().description = value
        changedProperty.add("description")
      }

    override fun getEntityData(): DefaultFieldEntityData = result ?: super.getEntityData() as DefaultFieldEntityData
    override fun getEntityClass(): Class<DefaultFieldEntity> = DefaultFieldEntity::class.java
  }
}

class DefaultFieldEntityData : WorkspaceEntityData<DefaultFieldEntity>() {
  var version: Int = 0
  lateinit var data: TestData
  var anotherVersion: Int = 0
  var description: String = "Default description"


  fun isDataInitialized(): Boolean = ::data.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<DefaultFieldEntity> {
    val modifiable = DefaultFieldEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): DefaultFieldEntity {
    val entity = DefaultFieldEntityImpl()
    entity.version = version
    entity._data = data
    entity.anotherVersion = anotherVersion
    entity.description = description
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return DefaultFieldEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as DefaultFieldEntityData

    if (this.version != other.version) return false
    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    if (this.anotherVersion != other.anotherVersion) return false
    if (this.description != other.description) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as DefaultFieldEntityData

    if (this.version != other.version) return false
    if (this.data != other.data) return false
    if (this.anotherVersion != other.anotherVersion) return false
    if (this.description != other.description) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + anotherVersion.hashCode()
    result = 31 * result + description.hashCode()
    return result
  }
}
