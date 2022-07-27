// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class JavaModuleSettingsEntityImpl : JavaModuleSettingsEntity, WorkspaceEntityBase() {

  companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, JavaModuleSettingsEntity::class.java,
                                                                          ConnectionId.ConnectionType.ONE_TO_ONE, false)

    val connections = listOf<ConnectionId>(
      MODULE_CONNECTION_ID,
    )

  }

  override val module: ModuleEntity
    get() = snapshot.extractOneToOneParent(MODULE_CONNECTION_ID, this)!!

  override var inheritedCompilerOutput: Boolean = false
  override var excludeOutput: Boolean = false
  @JvmField
  var _compilerOutput: VirtualFileUrl? = null
  override val compilerOutput: VirtualFileUrl?
    get() = _compilerOutput

  @JvmField
  var _compilerOutputForTests: VirtualFileUrl? = null
  override val compilerOutputForTests: VirtualFileUrl?
    get() = _compilerOutputForTests

  @JvmField
  var _languageLevelId: String? = null
  override val languageLevelId: String?
    get() = _languageLevelId

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: JavaModuleSettingsEntityData?) : ModifiableWorkspaceEntityBase<JavaModuleSettingsEntity>(), JavaModuleSettingsEntity.Builder {
    constructor() : this(JavaModuleSettingsEntityData())

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
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()

      index(this, "compilerOutput", this.compilerOutput)
      index(this, "compilerOutputForTests", this.compilerOutputForTests)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
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
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field JavaModuleSettingsEntity#entitySource should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }


    override var module: ModuleEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneParent(MODULE_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                 MODULE_CONNECTION_ID)]!! as ModuleEntity
        }
        else {
          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(MODULE_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
        }
        changedProperty.add("module")
      }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override var inheritedCompilerOutput: Boolean
      get() = getEntityData().inheritedCompilerOutput
      set(value) {
        checkModificationAllowed()
        getEntityData().inheritedCompilerOutput = value
        changedProperty.add("inheritedCompilerOutput")
      }

    override var excludeOutput: Boolean
      get() = getEntityData().excludeOutput
      set(value) {
        checkModificationAllowed()
        getEntityData().excludeOutput = value
        changedProperty.add("excludeOutput")
      }

    override var compilerOutput: VirtualFileUrl?
      get() = getEntityData().compilerOutput
      set(value) {
        checkModificationAllowed()
        getEntityData().compilerOutput = value
        changedProperty.add("compilerOutput")
        val _diff = diff
        if (_diff != null) index(this, "compilerOutput", value)
      }

    override var compilerOutputForTests: VirtualFileUrl?
      get() = getEntityData().compilerOutputForTests
      set(value) {
        checkModificationAllowed()
        getEntityData().compilerOutputForTests = value
        changedProperty.add("compilerOutputForTests")
        val _diff = diff
        if (_diff != null) index(this, "compilerOutputForTests", value)
      }

    override var languageLevelId: String?
      get() = getEntityData().languageLevelId
      set(value) {
        checkModificationAllowed()
        getEntityData().languageLevelId = value
        changedProperty.add("languageLevelId")
      }

    override fun getEntityData(): JavaModuleSettingsEntityData = result ?: super.getEntityData() as JavaModuleSettingsEntityData
    override fun getEntityClass(): Class<JavaModuleSettingsEntity> = JavaModuleSettingsEntity::class.java
  }
}

class JavaModuleSettingsEntityData : WorkspaceEntityData<JavaModuleSettingsEntity>() {
  var inheritedCompilerOutput: Boolean = false
  var excludeOutput: Boolean = false
  var compilerOutput: VirtualFileUrl? = null
  var compilerOutputForTests: VirtualFileUrl? = null
  var languageLevelId: String? = null


  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<JavaModuleSettingsEntity> {
    val modifiable = JavaModuleSettingsEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): JavaModuleSettingsEntity {
    val entity = JavaModuleSettingsEntityImpl()
    entity.inheritedCompilerOutput = inheritedCompilerOutput
    entity.excludeOutput = excludeOutput
    entity._compilerOutput = compilerOutput
    entity._compilerOutputForTests = compilerOutputForTests
    entity._languageLevelId = languageLevelId
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return JavaModuleSettingsEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as JavaModuleSettingsEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.inheritedCompilerOutput != other.inheritedCompilerOutput) return false
    if (this.excludeOutput != other.excludeOutput) return false
    if (this.compilerOutput != other.compilerOutput) return false
    if (this.compilerOutputForTests != other.compilerOutputForTests) return false
    if (this.languageLevelId != other.languageLevelId) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as JavaModuleSettingsEntityData

    if (this.inheritedCompilerOutput != other.inheritedCompilerOutput) return false
    if (this.excludeOutput != other.excludeOutput) return false
    if (this.compilerOutput != other.compilerOutput) return false
    if (this.compilerOutputForTests != other.compilerOutputForTests) return false
    if (this.languageLevelId != other.languageLevelId) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + inheritedCompilerOutput.hashCode()
    result = 31 * result + excludeOutput.hashCode()
    result = 31 * result + compilerOutput.hashCode()
    result = 31 * result + compilerOutputForTests.hashCode()
    result = 31 * result + languageLevelId.hashCode()
    return result
  }
}
