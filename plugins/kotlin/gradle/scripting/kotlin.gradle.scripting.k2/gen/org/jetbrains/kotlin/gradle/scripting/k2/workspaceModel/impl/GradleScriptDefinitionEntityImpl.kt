// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntityBuilder
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntityId
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptEvaluationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptingHostConfigurationEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleScriptDefinitionEntityImpl(private val dataSource: GradleScriptDefinitionEntityData) : GradleScriptDefinitionEntity, WorkspaceEntityBase(
  dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val symbolicId: GradleScriptDefinitionEntityId = super.symbolicId

  override val definitionId: String
    get() {
      readField("definitionId")
      return dataSource.definitionId
    }

  override val compilationConfiguration: ScriptCompilationConfigurationEntity
    get() {
      readField("compilationConfiguration")
      return dataSource.compilationConfiguration
    }

  override val hostConfiguration: ScriptingHostConfigurationEntity
    get() {
      readField("hostConfiguration")
      return dataSource.hostConfiguration
    }

  override val evaluationConfiguration: ScriptEvaluationConfigurationEntity?
    get() {
      readField("evaluationConfiguration")
      return dataSource.evaluationConfiguration
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: GradleScriptDefinitionEntityData?) : ModifiableWorkspaceEntityBase<GradleScriptDefinitionEntity, GradleScriptDefinitionEntityData>(
    result), GradleScriptDefinitionEntityBuilder {
    internal constructor() : this(GradleScriptDefinitionEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity GradleScriptDefinitionEntity is already created in a different builder")
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
      if (!getEntityData().isDefinitionIdInitialized()) {
        error("Field GradleScriptDefinitionEntity#definitionId should be initialized")
      }
      if (!getEntityData().isCompilationConfigurationInitialized()) {
        error("Field GradleScriptDefinitionEntity#compilationConfiguration should be initialized")
      }
      if (!getEntityData().isHostConfigurationInitialized()) {
        error("Field GradleScriptDefinitionEntity#hostConfiguration should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as GradleScriptDefinitionEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.definitionId != dataSource.definitionId) this.definitionId = dataSource.definitionId
      if (this.compilationConfiguration != dataSource.compilationConfiguration) this.compilationConfiguration = dataSource.compilationConfiguration
      if (this.hostConfiguration != dataSource.hostConfiguration) this.hostConfiguration = dataSource.hostConfiguration
      if (this.evaluationConfiguration != dataSource?.evaluationConfiguration) this.evaluationConfiguration = dataSource.evaluationConfiguration
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var definitionId: String
      get() = getEntityData().definitionId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).definitionId = value
        changedProperty.add("definitionId")
      }

    override var compilationConfiguration: ScriptCompilationConfigurationEntity
      get() = getEntityData().compilationConfiguration
      set(value) {
        checkModificationAllowed()
        getEntityData(true).compilationConfiguration = value
        changedProperty.add("compilationConfiguration")

      }

    override var hostConfiguration: ScriptingHostConfigurationEntity
      get() = getEntityData().hostConfiguration
      set(value) {
        checkModificationAllowed()
        getEntityData(true).hostConfiguration = value
        changedProperty.add("hostConfiguration")

      }

    override var evaluationConfiguration: ScriptEvaluationConfigurationEntity?
      get() = getEntityData().evaluationConfiguration
      set(value) {
        checkModificationAllowed()
        getEntityData(true).evaluationConfiguration = value
        changedProperty.add("evaluationConfiguration")

      }

    override fun getEntityClass(): Class<GradleScriptDefinitionEntity> = GradleScriptDefinitionEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleScriptDefinitionEntityData : WorkspaceEntityData<GradleScriptDefinitionEntity>() {
  lateinit var definitionId: String
  lateinit var compilationConfiguration: ScriptCompilationConfigurationEntity
  lateinit var hostConfiguration: ScriptingHostConfigurationEntity
  var evaluationConfiguration: ScriptEvaluationConfigurationEntity? = null

  internal fun isDefinitionIdInitialized(): Boolean = ::definitionId.isInitialized
  internal fun isCompilationConfigurationInitialized(): Boolean = ::compilationConfiguration.isInitialized
  internal fun isHostConfigurationInitialized(): Boolean = ::hostConfiguration.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<GradleScriptDefinitionEntity> {
    val modifiable = GradleScriptDefinitionEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): GradleScriptDefinitionEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = GradleScriptDefinitionEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return GradleScriptDefinitionEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return GradleScriptDefinitionEntity(definitionId, compilationConfiguration, hostConfiguration, entitySource) {
      this.evaluationConfiguration = this@GradleScriptDefinitionEntityData.evaluationConfiguration
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as GradleScriptDefinitionEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.definitionId != other.definitionId) return false
    if (this.compilationConfiguration != other.compilationConfiguration) return false
    if (this.hostConfiguration != other.hostConfiguration) return false
    if (this.evaluationConfiguration != other.evaluationConfiguration) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as GradleScriptDefinitionEntityData

    if (this.definitionId != other.definitionId) return false
    if (this.compilationConfiguration != other.compilationConfiguration) return false
    if (this.hostConfiguration != other.hostConfiguration) return false
    if (this.evaluationConfiguration != other.evaluationConfiguration) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + definitionId.hashCode()
    result = 31 * result + compilationConfiguration.hashCode()
    result = 31 * result + hostConfiguration.hashCode()
    result = 31 * result + evaluationConfiguration.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + definitionId.hashCode()
    result = 31 * result + compilationConfiguration.hashCode()
    result = 31 * result + hostConfiguration.hashCode()
    result = 31 * result + evaluationConfiguration.hashCode()
    return result
  }
}
