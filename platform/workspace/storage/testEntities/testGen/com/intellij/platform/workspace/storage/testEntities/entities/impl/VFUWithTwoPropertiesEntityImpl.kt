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
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.VFUWithTwoPropertiesEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class VFUWithTwoPropertiesEntityImpl(private val dataSource: VFUWithTwoPropertiesEntityData) : VFUWithTwoPropertiesEntity,
                                                                                                        WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val data: String
    get() {
      readField("data")
      return dataSource.data
    }

  override val fileProperty: VirtualFileUrl
    get() {
      readField("fileProperty")
      return dataSource.fileProperty
    }

  override val secondFileProperty: VirtualFileUrl
    get() {
      readField("secondFileProperty")
      return dataSource.secondFileProperty
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: VFUWithTwoPropertiesEntityData?) :
    ModifiableWorkspaceEntityBase<VFUWithTwoPropertiesEntity, VFUWithTwoPropertiesEntityData>(result), VFUWithTwoPropertiesEntity.Builder {
    internal constructor() : this(VFUWithTwoPropertiesEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity VFUWithTwoPropertiesEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "fileProperty", this.fileProperty)
      index(this, "secondFileProperty", this.secondFileProperty)
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
        error("Field VFUWithTwoPropertiesEntity#data should be initialized")
      }
      if (!getEntityData().isFilePropertyInitialized()) {
        error("Field VFUWithTwoPropertiesEntity#fileProperty should be initialized")
      }
      if (!getEntityData().isSecondFilePropertyInitialized()) {
        error("Field VFUWithTwoPropertiesEntity#secondFileProperty should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as VFUWithTwoPropertiesEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.data != dataSource.data) this.data = dataSource.data
      if (this.fileProperty != dataSource.fileProperty) this.fileProperty = dataSource.fileProperty
      if (this.secondFileProperty != dataSource.secondFileProperty) this.secondFileProperty = dataSource.secondFileProperty
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

    override var fileProperty: VirtualFileUrl
      get() = getEntityData().fileProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).fileProperty = value
        changedProperty.add("fileProperty")
        val _diff = diff
        if (_diff != null) index(this, "fileProperty", value)
      }

    override var secondFileProperty: VirtualFileUrl
      get() = getEntityData().secondFileProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).secondFileProperty = value
        changedProperty.add("secondFileProperty")
        val _diff = diff
        if (_diff != null) index(this, "secondFileProperty", value)
      }

    override fun getEntityClass(): Class<VFUWithTwoPropertiesEntity> = VFUWithTwoPropertiesEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class VFUWithTwoPropertiesEntityData : WorkspaceEntityData<VFUWithTwoPropertiesEntity>() {
  lateinit var data: String
  lateinit var fileProperty: VirtualFileUrl
  lateinit var secondFileProperty: VirtualFileUrl

  internal fun isDataInitialized(): Boolean = ::data.isInitialized
  internal fun isFilePropertyInitialized(): Boolean = ::fileProperty.isInitialized
  internal fun isSecondFilePropertyInitialized(): Boolean = ::secondFileProperty.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<VFUWithTwoPropertiesEntity> {
    val modifiable = VFUWithTwoPropertiesEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): VFUWithTwoPropertiesEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = VFUWithTwoPropertiesEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.VFUWithTwoPropertiesEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return VFUWithTwoPropertiesEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return VFUWithTwoPropertiesEntity(data, fileProperty, secondFileProperty, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as VFUWithTwoPropertiesEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.data != other.data) return false
    if (this.fileProperty != other.fileProperty) return false
    if (this.secondFileProperty != other.secondFileProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as VFUWithTwoPropertiesEntityData

    if (this.data != other.data) return false
    if (this.fileProperty != other.fileProperty) return false
    if (this.secondFileProperty != other.secondFileProperty) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + fileProperty.hashCode()
    result = 31 * result + secondFileProperty.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + fileProperty.hashCode()
    result = 31 * result + secondFileProperty.hashCode()
    return result
  }
}
