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
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.MutableWorkspaceSet
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class SetVFUEntityImpl(val dataSource: SetVFUEntityData) : SetVFUEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  override val data: String
    get() = dataSource.data

  override val fileProperty: Set<VirtualFileUrl>
    get() = dataSource.fileProperty

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(var result: SetVFUEntityData?) : ModifiableWorkspaceEntityBase<SetVFUEntity>(), SetVFUEntity.Builder {
    constructor() : this(SetVFUEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SetVFUEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.result = null

      index(this, "fileProperty", this.fileProperty.toHashSet())
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
        error("Field SetVFUEntity#data should be initialized")
      }
      if (!getEntityData().isFilePropertyInitialized()) {
        error("Field SetVFUEntity#fileProperty should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_fileProperty = getEntityData().fileProperty
      if (collection_fileProperty is MutableWorkspaceSet<*>) {
        collection_fileProperty.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SetVFUEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.data != dataSource.data) this.data = dataSource.data
      if (this.fileProperty != dataSource.fileProperty) this.fileProperty = dataSource.fileProperty.toMutableSet()
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

    private val filePropertyUpdater: (value: Set<VirtualFileUrl>) -> Unit = { value ->
      val _diff = diff
      if (_diff != null) index(this, "fileProperty", value.toHashSet())
      changedProperty.add("fileProperty")
    }
    override var fileProperty: MutableSet<VirtualFileUrl>
      get() {
        val collection_fileProperty = getEntityData().fileProperty
        if (collection_fileProperty !is MutableWorkspaceSet) return collection_fileProperty
        if (diff == null || modifiable.get()) {
          collection_fileProperty.setModificationUpdateAction(filePropertyUpdater)
        }
        else {
          collection_fileProperty.cleanModificationUpdateAction()
        }
        return collection_fileProperty
      }
      set(value) {
        checkModificationAllowed()
        getEntityData().fileProperty = value
        filePropertyUpdater.invoke(value)
      }

    override fun getEntityData(): SetVFUEntityData = result ?: super.getEntityData() as SetVFUEntityData
    override fun getEntityClass(): Class<SetVFUEntity> = SetVFUEntity::class.java
  }
}

class SetVFUEntityData : WorkspaceEntityData<SetVFUEntity>() {
  lateinit var data: String
  lateinit var fileProperty: MutableSet<VirtualFileUrl>

  fun isDataInitialized(): Boolean = ::data.isInitialized
  fun isFilePropertyInitialized(): Boolean = ::fileProperty.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SetVFUEntity> {
    val modifiable = SetVFUEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): SetVFUEntity {
    return getCached(snapshot) {
      val entity = SetVFUEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun clone(): SetVFUEntityData {
    val clonedEntity = super.clone()
    clonedEntity as SetVFUEntityData
    clonedEntity.fileProperty = clonedEntity.fileProperty.toMutableWorkspaceSet()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SetVFUEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return SetVFUEntity(data, fileProperty, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SetVFUEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    if (this.fileProperty != other.fileProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SetVFUEntityData

    if (this.data != other.data) return false
    if (this.fileProperty != other.fileProperty) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + fileProperty.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + fileProperty.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    this.fileProperty?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
