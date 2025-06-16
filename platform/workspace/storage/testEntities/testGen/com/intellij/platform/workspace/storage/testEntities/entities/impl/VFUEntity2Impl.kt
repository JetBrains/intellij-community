// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.VFUEntity2
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class VFUEntity2Impl(private val dataSource: VFUEntity2Data) : VFUEntity2, WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val data: String
    get() {
      readField("data")
      return dataSource.data
    }

  override val filePath: VirtualFileUrl?
    get() {
      readField("filePath")
      return dataSource.filePath
    }

  override val directoryPath: VirtualFileUrl
    get() {
      readField("directoryPath")
      return dataSource.directoryPath
    }

  override val notNullRoots: List<VirtualFileUrl>
    get() {
      readField("notNullRoots")
      return dataSource.notNullRoots
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: VFUEntity2Data?) : ModifiableWorkspaceEntityBase<VFUEntity2, VFUEntity2Data>(result), VFUEntity2.Builder {
    internal constructor() : this(VFUEntity2Data())

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
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "filePath", this.filePath)
      index(this, "directoryPath", this.directoryPath)
      index(this, "notNullRoots", this.notNullRoots)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isDataInitialized()) {
        error("Field VFUEntity2#data should be initialized")
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

    override fun afterModification() {
      val collection_notNullRoots = getEntityData().notNullRoots
      if (collection_notNullRoots is MutableWorkspaceList<*>) {
        collection_notNullRoots.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as VFUEntity2
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.data != dataSource.data) this.data = dataSource.data
      if (this.filePath != dataSource?.filePath) this.filePath = dataSource.filePath
      if (this.directoryPath != dataSource.directoryPath) this.directoryPath = dataSource.directoryPath
      if (this.notNullRoots != dataSource.notNullRoots) this.notNullRoots = dataSource.notNullRoots.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var data: String
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData(true).data = value
        changedProperty.add("data")
      }

    override var filePath: VirtualFileUrl?
      get() = getEntityData().filePath
      set(value) {
        checkModificationAllowed()
        getEntityData(true).filePath = value
        changedProperty.add("filePath")
        val _diff = diff
        if (_diff != null) index(this, "filePath", value)
      }

    override var directoryPath: VirtualFileUrl
      get() = getEntityData().directoryPath
      set(value) {
        checkModificationAllowed()
        getEntityData(true).directoryPath = value
        changedProperty.add("directoryPath")
        val _diff = diff
        if (_diff != null) index(this, "directoryPath", value)
      }

    private val notNullRootsUpdater: (value: List<VirtualFileUrl>) -> Unit = { value ->
      val _diff = diff
      if (_diff != null) index(this, "notNullRoots", value)
      changedProperty.add("notNullRoots")
    }
    override var notNullRoots: MutableList<VirtualFileUrl>
      get() {
        val collection_notNullRoots = getEntityData().notNullRoots
        if (collection_notNullRoots !is MutableWorkspaceList) return collection_notNullRoots
        if (diff == null || modifiable.get()) {
          collection_notNullRoots.setModificationUpdateAction(notNullRootsUpdater)
        }
        else {
          collection_notNullRoots.cleanModificationUpdateAction()
        }
        return collection_notNullRoots
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).notNullRoots = value
        notNullRootsUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<VFUEntity2> = VFUEntity2::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class VFUEntity2Data : WorkspaceEntityData<VFUEntity2>() {
  lateinit var data: String
  var filePath: VirtualFileUrl? = null
  lateinit var directoryPath: VirtualFileUrl
  lateinit var notNullRoots: MutableList<VirtualFileUrl>

  internal fun isDataInitialized(): Boolean = ::data.isInitialized
  internal fun isDirectoryPathInitialized(): Boolean = ::directoryPath.isInitialized
  internal fun isNotNullRootsInitialized(): Boolean = ::notNullRoots.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<VFUEntity2> {
    val modifiable = VFUEntity2Impl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): VFUEntity2 {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = VFUEntity2Impl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.VFUEntity2"
    ) as EntityMetadata
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

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return VFUEntity2(data, directoryPath, notNullRoots, entitySource) {
      this.filePath = this@VFUEntity2Data.filePath
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as VFUEntity2Data

    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    if (this.filePath != other.filePath) return false
    if (this.directoryPath != other.directoryPath) return false
    if (this.notNullRoots != other.notNullRoots) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

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

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + filePath.hashCode()
    result = 31 * result + directoryPath.hashCode()
    result = 31 * result + notNullRoots.hashCode()
    return result
  }
}
