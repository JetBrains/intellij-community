// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptEvaluationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptingHostConfigurationEntity

@GeneratedCodeApiVersion(3)
interface ModifiableGradleScriptDefinitionEntity : ModifiableWorkspaceEntity<GradleScriptDefinitionEntity> {
  override var entitySource: EntitySource
  var definitionId: String
  var compilationConfiguration: ScriptCompilationConfigurationEntity
  var hostConfiguration: ScriptingHostConfigurationEntity
  var evaluationConfiguration: ScriptEvaluationConfigurationEntity?
}

internal object GradleScriptDefinitionEntityType : EntityType<GradleScriptDefinitionEntity, ModifiableGradleScriptDefinitionEntity>() {
  override val entityClass: Class<GradleScriptDefinitionEntity> get() = GradleScriptDefinitionEntity::class.java
  operator fun invoke(
    definitionId: String,
    compilationConfiguration: ScriptCompilationConfigurationEntity,
    hostConfiguration: ScriptingHostConfigurationEntity,
    entitySource: EntitySource,
    init: (ModifiableGradleScriptDefinitionEntity.() -> Unit)? = null,
  ): ModifiableGradleScriptDefinitionEntity {
    val builder = builder()
    builder.definitionId = definitionId
    builder.compilationConfiguration = compilationConfiguration
    builder.hostConfiguration = hostConfiguration
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyGradleScriptDefinitionEntity(
  entity: GradleScriptDefinitionEntity,
  modification: ModifiableGradleScriptDefinitionEntity.() -> Unit,
): GradleScriptDefinitionEntity = modifyEntity(ModifiableGradleScriptDefinitionEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createGradleScriptDefinitionEntity")
fun GradleScriptDefinitionEntity(
  definitionId: String,
  compilationConfiguration: ScriptCompilationConfigurationEntity,
  hostConfiguration: ScriptingHostConfigurationEntity,
  entitySource: EntitySource,
  init: (ModifiableGradleScriptDefinitionEntity.() -> Unit)? = null,
): ModifiableGradleScriptDefinitionEntity =
  GradleScriptDefinitionEntityType(definitionId, compilationConfiguration, hostConfiguration, entitySource, init)
