// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.java.workspace.entities.impl

import com.intellij.java.workspace.entities.JavaProjectSettingsEntity
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntityBuilder
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class JavaProjectSettingsEntityImpl(private val dataSource: JavaProjectSettingsEntityData) : JavaProjectSettingsEntity,
                                                                                                      WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PROJECTSETTINGS_CONNECTION_ID: ConnectionId = ConnectionId.create(ProjectSettingsEntity::class.java,
                                                                                   JavaProjectSettingsEntity::class.java,
                                                                                   ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                   false)
    private val connections = listOf<ConnectionId>(PROJECTSETTINGS_CONNECTION_ID)
  }

  override val projectSettings: ProjectSettingsEntity
    get() = snapshot.instrumentation.getParent(PROJECTSETTINGS_CONNECTION_ID, this) as? ProjectSettingsEntity
            ?: error("Parent projectSettings not found for JavaProjectSettingsEntity")
  override val compilerOutput: VirtualFileUrl?
    get() {
      readField("compilerOutput")
      return dataSource.compilerOutput
    }
  override val languageLevelId: String?
    get() {
      readField("languageLevelId")
      return dataSource.languageLevelId
    }
  override val languageLevelDefault: Boolean?
    get() {
      readField("languageLevelDefault")
      return dataSource.languageLevelDefault
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: JavaProjectSettingsEntityData?) :
    ModifiableWorkspaceEntityBase<JavaProjectSettingsEntity, JavaProjectSettingsEntityData>(result), JavaProjectSettingsEntity.Builder {
    internal constructor() : this(JavaProjectSettingsEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PROJECTSETTINGS_CONNECTION_ID, this) == null) {
          error("Field JavaProjectSettingsEntity#projectSettings should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PROJECTSETTINGS_CONNECTION_ID)] == null) {
          error("Field JavaProjectSettingsEntity#projectSettings should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as JavaProjectSettingsEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.compilerOutput != dataSource?.compilerOutput) this.compilerOutput = dataSource.compilerOutput
      if (this.languageLevelId != dataSource?.languageLevelId) this.languageLevelId = dataSource.languageLevelId
      if (this.languageLevelDefault != dataSource?.languageLevelDefault) this.languageLevelDefault = dataSource.languageLevelDefault
      updateChildToParentReferences(parents)
    }

    override fun index() {
      index(this, "compilerOutput", this.compilerOutput)
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
          ?: error("projectSettings is null for JavaProjectSettingsEntity")
        }
        else {
          (this.entityLinks[EntityLink(false, PROJECTSETTINGS_CONNECTION_ID)] as? ProjectSettingsEntityBuilder)
          ?: error("projectSettings is null for JavaProjectSettingsEntity")
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          value.entityLinks[EntityLink(true, PROJECTSETTINGS_CONNECTION_ID)] = this
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(PROJECTSETTINGS_CONNECTION_ID, value, this)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, PROJECTSETTINGS_CONNECTION_ID)] = this
          }
          this.entityLinks[EntityLink(false, PROJECTSETTINGS_CONNECTION_ID)] = value
        }
        changedProperty.add("projectSettings")
      }
    override var compilerOutput: VirtualFileUrl?
      get() = getEntityData().compilerOutput
      set(value) {
        checkModificationAllowed()
        getEntityData(true).compilerOutput = value
        changedProperty.add("compilerOutput")
        val _diff = diff
        if (_diff != null) index(this, "compilerOutput", value)
      }
    override var languageLevelId: String?
      get() = getEntityData().languageLevelId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).languageLevelId = value
        changedProperty.add("languageLevelId")
      }
    override var languageLevelDefault: Boolean??
      get() = getEntityData().languageLevelDefault
      set(value) {
        checkModificationAllowed()
        getEntityData(true).languageLevelDefault = value
        changedProperty.add("languageLevelDefault")
      }

    override fun getEntityClass(): Class<JavaProjectSettingsEntity> = JavaProjectSettingsEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class JavaProjectSettingsEntityData : WorkspaceEntityData<JavaProjectSettingsEntity>() {
  var compilerOutput: VirtualFileUrl? = null
  var languageLevelId: String? = null
  var languageLevelDefault: Boolean? = null
  override fun newInstance(): JavaProjectSettingsEntity = JavaProjectSettingsEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<JavaProjectSettingsEntity, *> =
    JavaProjectSettingsEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.workspace.entities.JavaProjectSettingsEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return JavaProjectSettingsEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return JavaProjectSettingsEntity(entitySource) {
      this.compilerOutput = this@JavaProjectSettingsEntityData.compilerOutput
      this.languageLevelId = this@JavaProjectSettingsEntityData.languageLevelId
      this.languageLevelDefault = this@JavaProjectSettingsEntityData.languageLevelDefault
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
    other as JavaProjectSettingsEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.compilerOutput != other.compilerOutput) return false
    if (this.languageLevelId != other.languageLevelId) return false
    if (this.languageLevelDefault != other.languageLevelDefault) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as JavaProjectSettingsEntityData
    if (this.compilerOutput != other.compilerOutput) return false
    if (this.languageLevelId != other.languageLevelId) return false
    if (this.languageLevelDefault != other.languageLevelDefault) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + compilerOutput.hashCode()
    result = 31 * result + languageLevelId.hashCode()
    result = 31 * result + languageLevelDefault.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + compilerOutput.hashCode()
    result = 31 * result + languageLevelId.hashCode()
    result = 31 * result + languageLevelDefault.hashCode()
    return result
  }
}
