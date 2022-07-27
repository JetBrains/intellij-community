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
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class AssertConsistencyEntityImpl : AssertConsistencyEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  override var passCheck: Boolean = false

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: AssertConsistencyEntityData?) : ModifiableWorkspaceEntityBase<AssertConsistencyEntity>(), AssertConsistencyEntity.Builder {
    constructor() : this(AssertConsistencyEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity AssertConsistencyEntity is already created in a different builder")
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
        error("Field AssertConsistencyEntity#entitySource should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }


    override var passCheck: Boolean
      get() = getEntityData().passCheck
      set(value) {
        checkModificationAllowed()
        getEntityData().passCheck = value
        changedProperty.add("passCheck")
      }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override fun getEntityData(): AssertConsistencyEntityData = result ?: super.getEntityData() as AssertConsistencyEntityData
    override fun getEntityClass(): Class<AssertConsistencyEntity> = AssertConsistencyEntity::class.java
  }
}

class AssertConsistencyEntityData : WorkspaceEntityData<AssertConsistencyEntity>() {
  var passCheck: Boolean = false


  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<AssertConsistencyEntity> {
    val modifiable = AssertConsistencyEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): AssertConsistencyEntity {
    val entity = AssertConsistencyEntityImpl()
    entity.passCheck = passCheck
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return AssertConsistencyEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as AssertConsistencyEntityData

    if (this.passCheck != other.passCheck) return false
    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as AssertConsistencyEntityData

    if (this.passCheck != other.passCheck) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + passCheck.hashCode()
    return result
  }
}
