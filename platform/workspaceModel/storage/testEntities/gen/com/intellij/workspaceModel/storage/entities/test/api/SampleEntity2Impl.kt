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
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class SampleEntity2Impl(val dataSource: SampleEntity2Data) : SampleEntity2, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  override val data: String
    get() = dataSource.data

  override val boolData: Boolean get() = dataSource.boolData
  override val optionalData: String?
    get() = dataSource.optionalData

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: SampleEntity2Data?) : ModifiableWorkspaceEntityBase<SampleEntity2>(), SampleEntity2.Builder {
    constructor() : this(SampleEntity2Data())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SampleEntity2 is already created in a different builder")
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
      if (!getEntityData().isDataInitialized()) {
        error("Field SampleEntity2#data should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SampleEntity2
      this.entitySource = dataSource.entitySource
      this.data = dataSource.data
      this.boolData = dataSource.boolData
      this.optionalData = dataSource.optionalData
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

    override var data: String
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData().data = value
        changedProperty.add("data")
      }

    override var boolData: Boolean
      get() = getEntityData().boolData
      set(value) {
        checkModificationAllowed()
        getEntityData().boolData = value
        changedProperty.add("boolData")
      }

    override var optionalData: String?
      get() = getEntityData().optionalData
      set(value) {
        checkModificationAllowed()
        getEntityData().optionalData = value
        changedProperty.add("optionalData")
      }

    override fun getEntityData(): SampleEntity2Data = result ?: super.getEntityData() as SampleEntity2Data
    override fun getEntityClass(): Class<SampleEntity2> = SampleEntity2::class.java
  }
}

class SampleEntity2Data : WorkspaceEntityData<SampleEntity2>() {
  lateinit var data: String
  var boolData: Boolean = false
  var optionalData: String? = null

  fun isDataInitialized(): Boolean = ::data.isInitialized


  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<SampleEntity2> {
    val modifiable = SampleEntity2Impl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): SampleEntity2 {
    return getCached(snapshot) {
      val entity = SampleEntity2Impl(this)
      entity.entitySource = entitySource
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SampleEntity2::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return SampleEntity2(data, boolData, entitySource) {
      this.optionalData = this@SampleEntity2Data.optionalData
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as SampleEntity2Data

    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    if (this.boolData != other.boolData) return false
    if (this.optionalData != other.optionalData) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as SampleEntity2Data

    if (this.data != other.data) return false
    if (this.boolData != other.boolData) return false
    if (this.optionalData != other.optionalData) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + boolData.hashCode()
    result = 31 * result + optionalData.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + boolData.hashCode()
    result = 31 * result + optionalData.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
