// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities.impl

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
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
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class JavaModuleSettingsEntityImpl(private val dataSource: JavaModuleSettingsEntityData) : JavaModuleSettingsEntity,
                                                                                                    WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleEntity::class.java, JavaModuleSettingsEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)

    private val connections = listOf<ConnectionId>(
      MODULE_CONNECTION_ID,
    )

  }

  override val module: ModuleEntity
    get() = snapshot.extractOneToOneParent(MODULE_CONNECTION_ID, this)!!

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

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity JavaModuleSettingsEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "compilerOutput", this.compilerOutput)
      index(this, "compilerOutputForTests", this.compilerOutputForTests)
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


  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<JavaModuleSettingsEntity> {
    val modifiable = JavaModuleSettingsEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): JavaModuleSettingsEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = JavaModuleSettingsEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.workspace.entities.JavaModuleSettingsEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return JavaModuleSettingsEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return JavaModuleSettingsEntity(inheritedCompilerOutput, excludeOutput, entitySource) {
      this.compilerOutput = this@JavaModuleSettingsEntityData.compilerOutput
      this.compilerOutputForTests = this@JavaModuleSettingsEntityData.compilerOutputForTests
      this.languageLevelId = this@JavaModuleSettingsEntityData.languageLevelId
      this.manifestAttributes = this@JavaModuleSettingsEntityData.manifestAttributes
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
