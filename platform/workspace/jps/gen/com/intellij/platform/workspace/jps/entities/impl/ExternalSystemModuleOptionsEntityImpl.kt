// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleExtensions")

package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@Internal
@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ExternalSystemModuleOptionsEntityImpl(private val dataSource: ExternalSystemModuleOptionsEntityData) :
  ExternalSystemModuleOptionsEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(
      ModuleEntity::class.java, ExternalSystemModuleOptionsEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false
    )

    private val connections = listOf<ConnectionId>(
      MODULE_CONNECTION_ID,
    )

  }

  override val module: ModuleEntity
    get() = snapshot.extractOneToOneParent(MODULE_CONNECTION_ID, this)!!

  override val externalSystem: String?
    get() {
      readField("externalSystem")
      return dataSource.externalSystem
    }

  override val externalSystemModuleVersion: String?
    get() {
      readField("externalSystemModuleVersion")
      return dataSource.externalSystemModuleVersion
    }

  override val linkedProjectPath: String?
    get() {
      readField("linkedProjectPath")
      return dataSource.linkedProjectPath
    }

  override val linkedProjectId: String?
    get() {
      readField("linkedProjectId")
      return dataSource.linkedProjectId
    }

  override val rootProjectPath: String?
    get() {
      readField("rootProjectPath")
      return dataSource.rootProjectPath
    }

  override val externalSystemModuleGroup: String?
    get() {
      readField("externalSystemModuleGroup")
      return dataSource.externalSystemModuleGroup
    }

  override val externalSystemModuleType: String?
    get() {
      readField("externalSystemModuleType")
      return dataSource.externalSystemModuleType
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ExternalSystemModuleOptionsEntityData?) :
    ModifiableWorkspaceEntityBase<ExternalSystemModuleOptionsEntity, ExternalSystemModuleOptionsEntityData>(result),
    ExternalSystemModuleOptionsEntity.Builder {
    internal constructor() : this(ExternalSystemModuleOptionsEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ExternalSystemModuleOptionsEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToOneParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
          error("Field ExternalSystemModuleOptionsEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field ExternalSystemModuleOptionsEntity#module should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ExternalSystemModuleOptionsEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.externalSystem != dataSource?.externalSystem) this.externalSystem = dataSource.externalSystem
      if (this.externalSystemModuleVersion != dataSource?.externalSystemModuleVersion) this.externalSystemModuleVersion =
        dataSource.externalSystemModuleVersion
      if (this.linkedProjectPath != dataSource?.linkedProjectPath) this.linkedProjectPath = dataSource.linkedProjectPath
      if (this.linkedProjectId != dataSource?.linkedProjectId) this.linkedProjectId = dataSource.linkedProjectId
      if (this.rootProjectPath != dataSource?.rootProjectPath) this.rootProjectPath = dataSource.rootProjectPath
      if (this.externalSystemModuleGroup != dataSource?.externalSystemModuleGroup) this.externalSystemModuleGroup =
        dataSource.externalSystemModuleGroup
      if (this.externalSystemModuleType != dataSource?.externalSystemModuleType) this.externalSystemModuleType =
        dataSource.externalSystemModuleType
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var module: ModuleEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(MODULE_CONNECTION_ID, this) as? ModuleEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(MODULE_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
        }
        changedProperty.add("module")
      }

    override var externalSystem: String?
      get() = getEntityData().externalSystem
      set(value) {
        checkModificationAllowed()
        getEntityData(true).externalSystem = value
        changedProperty.add("externalSystem")
      }

    override var externalSystemModuleVersion: String?
      get() = getEntityData().externalSystemModuleVersion
      set(value) {
        checkModificationAllowed()
        getEntityData(true).externalSystemModuleVersion = value
        changedProperty.add("externalSystemModuleVersion")
      }

    override var linkedProjectPath: String?
      get() = getEntityData().linkedProjectPath
      set(value) {
        checkModificationAllowed()
        getEntityData(true).linkedProjectPath = value
        changedProperty.add("linkedProjectPath")
      }

    override var linkedProjectId: String?
      get() = getEntityData().linkedProjectId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).linkedProjectId = value
        changedProperty.add("linkedProjectId")
      }

    override var rootProjectPath: String?
      get() = getEntityData().rootProjectPath
      set(value) {
        checkModificationAllowed()
        getEntityData(true).rootProjectPath = value
        changedProperty.add("rootProjectPath")
      }

    override var externalSystemModuleGroup: String?
      get() = getEntityData().externalSystemModuleGroup
      set(value) {
        checkModificationAllowed()
        getEntityData(true).externalSystemModuleGroup = value
        changedProperty.add("externalSystemModuleGroup")
      }

    override var externalSystemModuleType: String?
      get() = getEntityData().externalSystemModuleType
      set(value) {
        checkModificationAllowed()
        getEntityData(true).externalSystemModuleType = value
        changedProperty.add("externalSystemModuleType")
      }

    override fun getEntityClass(): Class<ExternalSystemModuleOptionsEntity> = ExternalSystemModuleOptionsEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ExternalSystemModuleOptionsEntityData : WorkspaceEntityData<ExternalSystemModuleOptionsEntity>() {
  var externalSystem: String? = null
  var externalSystemModuleVersion: String? = null
  var linkedProjectPath: String? = null
  var linkedProjectId: String? = null
  var rootProjectPath: String? = null
  var externalSystemModuleGroup: String? = null
  var externalSystemModuleType: String? = null


  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ExternalSystemModuleOptionsEntity> {
    val modifiable = ExternalSystemModuleOptionsEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ExternalSystemModuleOptionsEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ExternalSystemModuleOptionsEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ExternalSystemModuleOptionsEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ExternalSystemModuleOptionsEntity(entitySource) {
      this.externalSystem = this@ExternalSystemModuleOptionsEntityData.externalSystem
      this.externalSystemModuleVersion = this@ExternalSystemModuleOptionsEntityData.externalSystemModuleVersion
      this.linkedProjectPath = this@ExternalSystemModuleOptionsEntityData.linkedProjectPath
      this.linkedProjectId = this@ExternalSystemModuleOptionsEntityData.linkedProjectId
      this.rootProjectPath = this@ExternalSystemModuleOptionsEntityData.rootProjectPath
      this.externalSystemModuleGroup = this@ExternalSystemModuleOptionsEntityData.externalSystemModuleGroup
      this.externalSystemModuleType = this@ExternalSystemModuleOptionsEntityData.externalSystemModuleType
      parents.filterIsInstance<ModuleEntity.Builder>().singleOrNull()?.let { this.module = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ExternalSystemModuleOptionsEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.externalSystem != other.externalSystem) return false
    if (this.externalSystemModuleVersion != other.externalSystemModuleVersion) return false
    if (this.linkedProjectPath != other.linkedProjectPath) return false
    if (this.linkedProjectId != other.linkedProjectId) return false
    if (this.rootProjectPath != other.rootProjectPath) return false
    if (this.externalSystemModuleGroup != other.externalSystemModuleGroup) return false
    if (this.externalSystemModuleType != other.externalSystemModuleType) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ExternalSystemModuleOptionsEntityData

    if (this.externalSystem != other.externalSystem) return false
    if (this.externalSystemModuleVersion != other.externalSystemModuleVersion) return false
    if (this.linkedProjectPath != other.linkedProjectPath) return false
    if (this.linkedProjectId != other.linkedProjectId) return false
    if (this.rootProjectPath != other.rootProjectPath) return false
    if (this.externalSystemModuleGroup != other.externalSystemModuleGroup) return false
    if (this.externalSystemModuleType != other.externalSystemModuleType) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + externalSystem.hashCode()
    result = 31 * result + externalSystemModuleVersion.hashCode()
    result = 31 * result + linkedProjectPath.hashCode()
    result = 31 * result + linkedProjectId.hashCode()
    result = 31 * result + rootProjectPath.hashCode()
    result = 31 * result + externalSystemModuleGroup.hashCode()
    result = 31 * result + externalSystemModuleType.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + externalSystem.hashCode()
    result = 31 * result + externalSystemModuleVersion.hashCode()
    result = 31 * result + linkedProjectPath.hashCode()
    result = 31 * result + linkedProjectId.hashCode()
    result = 31 * result + rootProjectPath.hashCode()
    result = 31 * result + externalSystemModuleGroup.hashCode()
    result = 31 * result + externalSystemModuleType.hashCode()
    return result
  }
}
