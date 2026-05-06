// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.importing

import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel
import org.gradle.tooling.model.dsl.KotlinDslBaseScriptModel
import org.jetbrains.kotlin.gradle.scripting.k2.GradleKotlinScriptEntityProvider
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleKotlinScriptEntitySource
import org.jetbrains.kotlin.gradle.scripting.shared.definition.BaseScriptDefinition
import org.jetbrains.kotlin.gradle.scripting.shared.definition.ErrorGradleScriptDefinition
import org.jetbrains.kotlin.gradle.scripting.shared.definition.GradleScriptDefinition
import org.jetbrains.kotlin.gradle.scripting.shared.definition.getGradleTemplatesNames
import org.jetbrains.kotlin.idea.core.script.shared.definition.loadDefinitionsFromTemplates
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.jvm.JvmDependency

internal class KotlinDslBaseScriptSyncContributor : GradleSyncContributor {
    override val name: String = "Kotlin DSL Base Script"

    override val phase: GradleSyncPhase = GradleSyncPhase.BASE_SCRIPT_MODEL_PHASE

    override suspend fun createProjectModel(
        context: ProjectResolverContext,
        storage: ImmutableEntityStorage
    ): ImmutableEntityStorage {
        val baseKotlinDslModel = context.getRootModel(GradleDslBaseScriptModel::class.java)?.kotlinDslBaseScriptModel ?: return storage
        val baseScriptDefinitions = loadBaseScriptDefinitions(context, baseKotlinDslModel)
        val entitySource = GradleKotlinDslBaseScriptEntitySource(context.projectPath, phase)

        // Contribute only fallback definitions, not script entities.
        // Open scripts may be represented as generic Kotlin-owned entities by `KotlinScriptService`
        // until SCRIPT_MODEL_PHASE contributes precise Gradle-owned entities.
        return GradleKotlinScriptEntityProvider.getInstance(context.project).getUpdatedStorage(
            storage.toBuilder(),
            entitySource,
            emptyList<GradleScriptModel>(),
            baseScriptDefinitions,
        )
    }

    private fun loadBaseScriptDefinitions(
        context: ProjectResolverContext,
        baseKotlinDslModel: KotlinDslBaseScriptModel
    ): List<GradleScriptDefinition> {
        return loadDefinitionsFromTemplates(
            getGradleTemplatesNames(context.gradleVersion),
            (baseKotlinDslModel.scriptTemplatesClassPath + baseKotlinDslModel.compileClassPath).map { it.toPath() }
        ).map {
            BaseScriptDefinition(it)
        }.ifEmpty {
            sequenceOf(ErrorGradleScriptDefinition())
        }.toList().map {
            it.with {
                dependencies(JvmDependency(baseKotlinDslModel.compileClassPath))
                defaultImports(baseKotlinDslModel.implicitImports)
            }
        }
    }
}

private data class GradleKotlinDslBaseScriptEntitySource(
    override val projectPath: String,
    override val phase: GradleSyncPhase
) : GradleKotlinScriptEntitySource
