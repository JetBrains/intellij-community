// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.java.workspace.entities.impl

import com.intellij.java.workspace.entities.JavaCompilerProjectSettingsEntity
import com.intellij.java.workspace.entities.JavaCompilerProjectSettingsEntityBuilder
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntityBuilder
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class JavaCompilerProjectSettingsEntityImpl(private val dataSource: JavaCompilerProjectSettingsEntityData) :
  JavaCompilerProjectSettingsEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val PROJECTSETTINGS_CONNECTION_ID: ConnectionId = ConnectionId.create(ProjectSettingsEntity::class.java,
                                                                                   JavaCompilerProjectSettingsEntity::class.java,
                                                                                   ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                   false)
    private val connections = listOf<ConnectionId>(PROJECTSETTINGS_CONNECTION_ID)

  }

  override val projectSettings: ProjectSettingsEntity
    get() = snapshot.instrumentation.getParent(PROJECTSETTINGS_CONNECTION_ID, this) as? ProjectSettingsEntity
            ?: error("Parent projectSettings not found for JavaCompilerProjectSettingsEntity")
  override val additionalOptions: List<String>
    get() {
      readField("additionalOptions")
      return dataSource.additionalOptions
    }
  override var preferTargetJdkCompiler: Boolean = dataSource.preferTargetJdkCompiler

  override var debuggingInfo: Boolean = dataSource.debuggingInfo

  override var generateNoWarnings: Boolean = dataSource.generateNoWarnings

  override var deprecation: Boolean = dataSource.deprecation

  override var maximumHeapSize: Int = dataSource.maximumHeapSize

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: JavaCompilerProjectSettingsEntityData?) :
    ModifiableWorkspaceEntityBase<JavaCompilerProjectSettingsEntity, JavaCompilerProjectSettingsEntityData>(result),
    JavaCompilerProjectSettingsEntityBuilder {
    internal constructor() : this(JavaCompilerProjectSettingsEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity JavaCompilerProjectSettingsEntity is already created in a different builder")
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
        if (_diff.instrumentation.getParentBuilder(PROJECTSETTINGS_CONNECTION_ID, this) == null) {
          error("Field JavaCompilerProjectSettingsEntity#projectSettings should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PROJECTSETTINGS_CONNECTION_ID)] == null) {
          error("Field JavaCompilerProjectSettingsEntity#projectSettings should be initialized")
        }
      }
      if (!getEntityData().isAdditionalOptionsInitialized()) {
        error("Field JavaCompilerProjectSettingsEntity#additionalOptions should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_additionalOptions = getEntityData().additionalOptions
      if (collection_additionalOptions is MutableWorkspaceList<*>) {
        collection_additionalOptions.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as JavaCompilerProjectSettingsEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.additionalOptions != dataSource.additionalOptions) this.additionalOptions = dataSource.additionalOptions.toMutableList()
      if (this.preferTargetJdkCompiler != dataSource.preferTargetJdkCompiler) this.preferTargetJdkCompiler =
        dataSource.preferTargetJdkCompiler
      if (this.debuggingInfo != dataSource.debuggingInfo) this.debuggingInfo = dataSource.debuggingInfo
      if (this.generateNoWarnings != dataSource.generateNoWarnings) this.generateNoWarnings = dataSource.generateNoWarnings
      if (this.deprecation != dataSource.deprecation) this.deprecation = dataSource.deprecation
      if (this.maximumHeapSize != dataSource.maximumHeapSize) this.maximumHeapSize = dataSource.maximumHeapSize
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }
    override var projectSettings: ProjectSettingsEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PROJECTSETTINGS_CONNECTION_ID,
                                                                           this) as? ProjectSettingsEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, PROJECTSETTINGS_CONNECTION_ID)] as? ProjectSettingsEntityBuilder)
          ?: error("projectSettings is null for JavaCompilerProjectSettingsEntity")
        }
        else {
          (this.entityLinks[EntityLink(false, PROJECTSETTINGS_CONNECTION_ID)] as? ProjectSettingsEntityBuilder)
          ?: error("projectSettings is null for JavaCompilerProjectSettingsEntity")
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, PROJECTSETTINGS_CONNECTION_ID)] = this
          }
// else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(PROJECTSETTINGS_CONNECTION_ID, value, this)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, PROJECTSETTINGS_CONNECTION_ID)] = this
          }
// else you're attaching a new entity to an existing entity that is not modifiable
          this.entityLinks[EntityLink(false, PROJECTSETTINGS_CONNECTION_ID)] = value
        }
        changedProperty.add("projectSettings")
      }

    private val additionalOptionsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("additionalOptions")
    }
    override var additionalOptions: MutableList<String>
      get() {
        val collection_additionalOptions = getEntityData().additionalOptions
        if (collection_additionalOptions !is MutableWorkspaceList) return collection_additionalOptions
        if (diff == null || modifiable.get()) {
          collection_additionalOptions.setModificationUpdateAction(additionalOptionsUpdater)
        }
        else {
          collection_additionalOptions.cleanModificationUpdateAction()
        }
        return collection_additionalOptions
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).additionalOptions = value
        additionalOptionsUpdater.invoke(value)
      }
    override var preferTargetJdkCompiler: Boolean
      get() = getEntityData().preferTargetJdkCompiler
      set(value) {
        checkModificationAllowed()
        getEntityData(true).preferTargetJdkCompiler = value
        changedProperty.add("preferTargetJdkCompiler")
      }
    override var debuggingInfo: Boolean
      get() = getEntityData().debuggingInfo
      set(value) {
        checkModificationAllowed()
        getEntityData(true).debuggingInfo = value
        changedProperty.add("debuggingInfo")
      }
    override var generateNoWarnings: Boolean
      get() = getEntityData().generateNoWarnings
      set(value) {
        checkModificationAllowed()
        getEntityData(true).generateNoWarnings = value
        changedProperty.add("generateNoWarnings")
      }
    override var deprecation: Boolean
      get() = getEntityData().deprecation
      set(value) {
        checkModificationAllowed()
        getEntityData(true).deprecation = value
        changedProperty.add("deprecation")
      }
    override var maximumHeapSize: Int
      get() = getEntityData().maximumHeapSize
      set(value) {
        checkModificationAllowed()
        getEntityData(true).maximumHeapSize = value
        changedProperty.add("maximumHeapSize")
      }

    override fun getEntityClass(): Class<JavaCompilerProjectSettingsEntity> = JavaCompilerProjectSettingsEntity::class.java
  }

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class JavaCompilerProjectSettingsEntityData : WorkspaceEntityData<JavaCompilerProjectSettingsEntity>() {
  lateinit var additionalOptions: MutableList<String>
  var preferTargetJdkCompiler: Boolean = true
  var debuggingInfo: Boolean = true
  var generateNoWarnings: Boolean = false
  var deprecation: Boolean = true
  var maximumHeapSize: Int = 128

  internal fun isAdditionalOptionsInitialized(): Boolean = ::additionalOptions.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<JavaCompilerProjectSettingsEntity> {
    val modifiable = JavaCompilerProjectSettingsEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorageInstrumentation): JavaCompilerProjectSettingsEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = JavaCompilerProjectSettingsEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.workspace.entities.JavaCompilerProjectSettingsEntity") as EntityMetadata
  }

  override fun clone(): JavaCompilerProjectSettingsEntityData {
    val clonedEntity = super.clone()
    clonedEntity as JavaCompilerProjectSettingsEntityData
    clonedEntity.additionalOptions = clonedEntity.additionalOptions.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return JavaCompilerProjectSettingsEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return JavaCompilerProjectSettingsEntity(additionalOptions, entitySource) {
      this.preferTargetJdkCompiler = this@JavaCompilerProjectSettingsEntityData.preferTargetJdkCompiler
      this.debuggingInfo = this@JavaCompilerProjectSettingsEntityData.debuggingInfo
      this.generateNoWarnings = this@JavaCompilerProjectSettingsEntityData.generateNoWarnings
      this.deprecation = this@JavaCompilerProjectSettingsEntityData.deprecation
      this.maximumHeapSize = this@JavaCompilerProjectSettingsEntityData.maximumHeapSize
      parents.filterIsInstance<ProjectSettingsEntityBuilder>().singleOrNull()?.let { this.projectSettings = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ProjectSettingsEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as JavaCompilerProjectSettingsEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.additionalOptions != other.additionalOptions) return false
    if (this.preferTargetJdkCompiler != other.preferTargetJdkCompiler) return false
    if (this.debuggingInfo != other.debuggingInfo) return false
    if (this.generateNoWarnings != other.generateNoWarnings) return false
    if (this.deprecation != other.deprecation) return false
    if (this.maximumHeapSize != other.maximumHeapSize) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as JavaCompilerProjectSettingsEntityData
    if (this.additionalOptions != other.additionalOptions) return false
    if (this.preferTargetJdkCompiler != other.preferTargetJdkCompiler) return false
    if (this.debuggingInfo != other.debuggingInfo) return false
    if (this.generateNoWarnings != other.generateNoWarnings) return false
    if (this.deprecation != other.deprecation) return false
    if (this.maximumHeapSize != other.maximumHeapSize) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + additionalOptions.hashCode()
    result = 31 * result + preferTargetJdkCompiler.hashCode()
    result = 31 * result + debuggingInfo.hashCode()
    result = 31 * result + generateNoWarnings.hashCode()
    result = 31 * result + deprecation.hashCode()
    result = 31 * result + maximumHeapSize.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + additionalOptions.hashCode()
    result = 31 * result + preferTargetJdkCompiler.hashCode()
    result = 31 * result + debuggingInfo.hashCode()
    result = 31 * result + generateNoWarnings.hashCode()
    result = 31 * result + deprecation.hashCode()
    result = 31 * result + maximumHeapSize.hashCode()
    return result
  }
}
