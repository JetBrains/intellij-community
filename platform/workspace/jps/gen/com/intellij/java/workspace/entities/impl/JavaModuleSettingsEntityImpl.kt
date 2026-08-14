// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.java.workspace.entities.impl

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
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
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class JavaModuleSettingsEntityImpl(private val dataSource: JavaModuleSettingsEntityData) : JavaModuleSettingsEntity,
                                                                                                    WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleEntity::class.java, JavaModuleSettingsEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val connections = listOf<ConnectionId>(MODULE_CONNECTION_ID)
  }

  override val module: ModuleEntity
    get() = snapshot.instrumentation.getParent(MODULE_CONNECTION_ID, this) as? ModuleEntity
            ?: error("Parent module not found for JavaModuleSettingsEntity")
  override val inheritedCompilerOutput: Boolean
    get() {
      readField("inheritedCompilerOutput")
      return dataSource.inheritedCompilerOutput
    }
  override val excludeOutput: Boolean
    get() {
      readField("excludeOutput")
      return dataSource.excludeOutput
    }
  override val compilerOutput: VirtualFileUrl?
    get() {
      readField("compilerOutput")
      return dataSource.compilerOutput
    }
  override val compilerOutputForTests: VirtualFileUrl?
    get() {
      readField("compilerOutputForTests")
      return dataSource.compilerOutputForTests
    }
  override val languageLevelId: String?
    get() {
      readField("languageLevelId")
      return dataSource.languageLevelId
    }
  override var manifestAttributes: Map<String, String> = dataSource.manifestAttributes
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: JavaModuleSettingsEntityData?) :
    ModifiableWorkspaceEntityBase<JavaModuleSettingsEntity, JavaModuleSettingsEntityData>(result), JavaModuleSettingsEntity.Builder {
    internal constructor() : this(JavaModuleSettingsEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(MODULE_CONNECTION_ID, this) == null) {
          error("Field JavaModuleSettingsEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field JavaModuleSettingsEntity#module should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as JavaModuleSettingsEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.inheritedCompilerOutput != dataSource.inheritedCompilerOutput) this.inheritedCompilerOutput =
        dataSource.inheritedCompilerOutput
      if (this.excludeOutput != dataSource.excludeOutput) this.excludeOutput = dataSource.excludeOutput
      if (this.compilerOutput != dataSource?.compilerOutput) this.compilerOutput = dataSource.compilerOutput
      if (this.compilerOutputForTests != dataSource?.compilerOutputForTests) this.compilerOutputForTests = dataSource.compilerOutputForTests
      if (this.languageLevelId != dataSource?.languageLevelId) this.languageLevelId = dataSource.languageLevelId
      if (this.manifestAttributes != dataSource.manifestAttributes) this.manifestAttributes = dataSource.manifestAttributes.toMutableMap()
      updateChildToParentReferences(parents)
    }

    override fun index() {
      index(this, "compilerOutput", this.compilerOutput)
      index(this, "compilerOutputForTests", this.compilerOutputForTests)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var module: ModuleEntityBuilder
      get() = getParent(MODULE_CONNECTION_ID) as? ModuleEntityBuilder ?: error("module is null for JavaModuleSettingsEntity")
      set(value) {
        changeParent(value, MODULE_CONNECTION_ID)
        changedProperty.add("module")
      }
    override var inheritedCompilerOutput: Boolean
      get() = getEntityData().inheritedCompilerOutput
      set(value) {
        checkModificationAllowed()
        getEntityData(true).inheritedCompilerOutput = value
        changedProperty.add("inheritedCompilerOutput")
      }
    override var excludeOutput: Boolean
      get() = getEntityData().excludeOutput
      set(value) {
        checkModificationAllowed()
        getEntityData(true).excludeOutput = value
        changedProperty.add("excludeOutput")
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
    override var compilerOutputForTests: VirtualFileUrl?
      get() = getEntityData().compilerOutputForTests
      set(value) {
        checkModificationAllowed()
        getEntityData(true).compilerOutputForTests = value
        changedProperty.add("compilerOutputForTests")
        val _diff = diff
        if (_diff != null) index(this, "compilerOutputForTests", value)
      }
    override var languageLevelId: String?
      get() = getEntityData().languageLevelId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).languageLevelId = value
        changedProperty.add("languageLevelId")
      }
    override var manifestAttributes: Map<String, String>
      get() = getEntityData().manifestAttributes
      set(value) {
        checkModificationAllowed()
        getEntityData(true).manifestAttributes = value
        changedProperty.add("manifestAttributes")
      }

    override fun getEntityClass(): Class<JavaModuleSettingsEntity> = JavaModuleSettingsEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class JavaModuleSettingsEntityData : WorkspaceEntityData<JavaModuleSettingsEntity>() {
  var inheritedCompilerOutput: Boolean = false
  var excludeOutput: Boolean = false
  var compilerOutput: VirtualFileUrl? = null
  var compilerOutputForTests: VirtualFileUrl? = null
  var languageLevelId: String? = null
  var manifestAttributes: Map<String, String> = emptyMap()
  override fun newInstance(): JavaModuleSettingsEntity = JavaModuleSettingsEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<JavaModuleSettingsEntity, *> = JavaModuleSettingsEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.workspace.entities.JavaModuleSettingsEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return JavaModuleSettingsEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return JavaModuleSettingsEntity(inheritedCompilerOutput, excludeOutput, entitySource) {
      this.compilerOutput = this@JavaModuleSettingsEntityData.compilerOutput
      this.compilerOutputForTests = this@JavaModuleSettingsEntityData.compilerOutputForTests
      this.languageLevelId = this@JavaModuleSettingsEntityData.languageLevelId
      this.manifestAttributes = this@JavaModuleSettingsEntityData.manifestAttributes
      parents.filterIsInstance<ModuleEntityBuilder>().singleOrNull()?.let { this.module = it }
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
    other as JavaModuleSettingsEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.inheritedCompilerOutput != other.inheritedCompilerOutput) return false
    if (this.excludeOutput != other.excludeOutput) return false
    if (this.compilerOutput != other.compilerOutput) return false
    if (this.compilerOutputForTests != other.compilerOutputForTests) return false
    if (this.languageLevelId != other.languageLevelId) return false
    if (this.manifestAttributes != other.manifestAttributes) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as JavaModuleSettingsEntityData
    if (this.inheritedCompilerOutput != other.inheritedCompilerOutput) return false
    if (this.excludeOutput != other.excludeOutput) return false
    if (this.compilerOutput != other.compilerOutput) return false
    if (this.compilerOutputForTests != other.compilerOutputForTests) return false
    if (this.languageLevelId != other.languageLevelId) return false
    if (this.manifestAttributes != other.manifestAttributes) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + inheritedCompilerOutput.hashCode()
    result = 31 * result + excludeOutput.hashCode()
    result = 31 * result + compilerOutput.hashCode()
    result = 31 * result + compilerOutputForTests.hashCode()
    result = 31 * result + languageLevelId.hashCode()
    result = 31 * result + manifestAttributes.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + inheritedCompilerOutput.hashCode()
    result = 31 * result + excludeOutput.hashCode()
    result = 31 * result + compilerOutput.hashCode()
    result = 31 * result + compilerOutputForTests.hashCode()
    result = 31 * result + languageLevelId.hashCode()
    result = 31 * result + manifestAttributes.hashCode()
    return result
  }
}
