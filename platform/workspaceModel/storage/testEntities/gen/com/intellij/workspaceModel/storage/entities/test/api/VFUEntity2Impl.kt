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
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.MutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class VFUEntity2Impl : VFUEntity2, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  @JvmField
  var _data: String? = null
  override val data: String
    get() = _data!!

  @JvmField
  var _filePath: VirtualFileUrl? = null
  override val filePath: VirtualFileUrl?
    get() = _filePath

  @JvmField
  var _directoryPath: VirtualFileUrl? = null
  override val directoryPath: VirtualFileUrl
    get() = _directoryPath!!

  @JvmField
  var _notNullRoots: List<VirtualFileUrl>? = null
  override val notNullRoots: List<VirtualFileUrl>
    get() = _notNullRoots!!

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: VFUEntity2Data?) : ModifiableWorkspaceEntityBase<VFUEntity2>(), VFUEntity2.Builder {
    constructor() : this(VFUEntity2Data())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity VFUEntity2 is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()

      index(this, "filePath", this.filePath)
      index(this, "directoryPath", this.directoryPath)
      index(this, "notNullRoots", this.notNullRoots.toHashSet())
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isDataInitialized()) {
        error("Field VFUEntity2#data should be initialized")
      }
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field VFUEntity2#entitySource should be initialized")
      }
      if (!getEntityData().isDirectoryPathInitialized()) {
        error("Field VFUEntity2#directoryPath should be initialized")
      }
      if (!getEntityData().isNotNullRootsInitialized()) {
        error("Field VFUEntity2#notNullRoots should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }


    override var data: String
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData().data = value
        changedProperty.add("data")
      }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override var filePath: VirtualFileUrl?
      get() = getEntityData().filePath
      set(value) {
        checkModificationAllowed()
        getEntityData().filePath = value
        changedProperty.add("filePath")
        val _diff = diff
        if (_diff != null) index(this, "filePath", value)
      }

    override var directoryPath: VirtualFileUrl
      get() = getEntityData().directoryPath
      set(value) {
        checkModificationAllowed()
        getEntityData().directoryPath = value
        changedProperty.add("directoryPath")
        val _diff = diff
        if (_diff != null) index(this, "directoryPath", value)
      }

    private val notNullRootsUpdater: (value: List<VirtualFileUrl>) -> Unit = { value ->
      val _diff = diff
      if (_diff != null) index(this, "notNullRoots", value.toHashSet())
      changedProperty.add("notNullRoots")
    }
    override var notNullRoots: MutableList<VirtualFileUrl>
      get() {
        val collection_notNullRoots = getEntityData().notNullRoots
        if (collection_notNullRoots !is MutableWorkspaceList) return collection_notNullRoots
        collection_notNullRoots.setModificationUpdateAction(notNullRootsUpdater)
        return collection_notNullRoots
      }
      set(value) {
        checkModificationAllowed()
        getEntityData().notNullRoots = value
        notNullRootsUpdater.invoke(value)
      }

    override fun getEntityData(): VFUEntity2Data = result ?: super.getEntityData() as VFUEntity2Data
    override fun getEntityClass(): Class<VFUEntity2> = VFUEntity2::class.java
  }
}

class VFUEntity2Data : WorkspaceEntityData<VFUEntity2>() {
  lateinit var data: String
  var filePath: VirtualFileUrl? = null
  lateinit var directoryPath: VirtualFileUrl
  lateinit var notNullRoots: MutableList<VirtualFileUrl>

  fun isDataInitialized(): Boolean = ::data.isInitialized
  fun isDirectoryPathInitialized(): Boolean = ::directoryPath.isInitialized
  fun isNotNullRootsInitialized(): Boolean = ::notNullRoots.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<VFUEntity2> {
    val modifiable = VFUEntity2Impl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): VFUEntity2 {
    val entity = VFUEntity2Impl()
    entity._data = data
    entity._filePath = filePath
    entity._directoryPath = directoryPath
    entity._notNullRoots = notNullRoots.toList()
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
  }

  override fun clone(): VFUEntity2Data {
    val clonedEntity = super.clone()
    clonedEntity as VFUEntity2Data
    clonedEntity.notNullRoots = clonedEntity.notNullRoots.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return VFUEntity2::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as VFUEntity2Data

    if (this.data != other.data) return false
    if (this.entitySource != other.entitySource) return false
    if (this.filePath != other.filePath) return false
    if (this.directoryPath != other.directoryPath) return false
    if (this.notNullRoots != other.notNullRoots) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as VFUEntity2Data

    if (this.data != other.data) return false
    if (this.filePath != other.filePath) return false
    if (this.directoryPath != other.directoryPath) return false
    if (this.notNullRoots != other.notNullRoots) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + filePath.hashCode()
    result = 31 * result + directoryPath.hashCode()
    result = 31 * result + notNullRoots.hashCode()
    return result
  }
}
