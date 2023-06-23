package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Open
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.UsedClassesCollector
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(2)
open class ChildEntityImpl(val dataSource: ChildEntityData) : ChildEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  override val data1: String
    get() = dataSource.data1

  override val data2: String
    get() = dataSource.data2

  override val data3: String
    get() = dataSource.data3

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: ChildEntityData?) : ModifiableWorkspaceEntityBase<ChildEntity, ChildEntityData>(result), ChildEntity.Builder {
    constructor() : this(ChildEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ChildEntity is already created in a different builder")
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
      if (!getEntityData().isData1Initialized()) {
        error("Field GrandParentEntity#data1 should be initialized")
      }
      if (!getEntityData().isData2Initialized()) {
        error("Field ParentEntity#data2 should be initialized")
      }
      if (!getEntityData().isData3Initialized()) {
        error("Field ChildEntity#data3 should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ChildEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.data1 != dataSource.data1) this.data1 = dataSource.data1
      if (this.data2 != dataSource.data2) this.data2 = dataSource.data2
      if (this.data3 != dataSource.data3) this.data3 = dataSource.data3
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var data1: String
      get() = getEntityData().data1
      set(value) {
        checkModificationAllowed()
        getEntityData(true).data1 = value
        changedProperty.add("data1")
      }

    override var data2: String
      get() = getEntityData().data2
      set(value) {
        checkModificationAllowed()
        getEntityData(true).data2 = value
        changedProperty.add("data2")
      }

    override var data3: String
      get() = getEntityData().data3
      set(value) {
        checkModificationAllowed()
        getEntityData(true).data3 = value
        changedProperty.add("data3")
      }

    override fun getEntityClass(): Class<ChildEntity> = ChildEntity::class.java
  }
}

class ChildEntityData : WorkspaceEntityData<ChildEntity>() {
  lateinit var data1: String
  lateinit var data2: String
  lateinit var data3: String

  fun isData1Initialized(): Boolean = ::data1.isInitialized
  fun isData2Initialized(): Boolean = ::data2.isInitialized
  fun isData3Initialized(): Boolean = ::data3.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ChildEntity> {
    val modifiable = ChildEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ChildEntity {
    return getCached(snapshot) {
      val entity = ChildEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ChildEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return ChildEntity(data1, data2, data3, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ChildEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.data1 != other.data1) return false
    if (this.data2 != other.data2) return false
    if (this.data3 != other.data3) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ChildEntityData

    if (this.data1 != other.data1) return false
    if (this.data2 != other.data2) return false
    if (this.data3 != other.data3) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + data1.hashCode()
    result = 31 * result + data2.hashCode()
    result = 31 * result + data3.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + data1.hashCode()
    result = 31 * result + data2.hashCode()
    result = 31 * result + data3.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
