// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.importing

import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel
import org.jetbrains.kotlin.gradle.scripting.k2.GradleKotlinScriptService
import org.jetbrains.kotlin.gradle.scripting.shared.definition.BaseScriptDefinition
import org.jetbrains.kotlin.gradle.scripting.shared.definition.ErrorGradleScriptDefinition
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
        context: ProjectResolverContext, storage: ImmutableEntityStorage
    ): ImmutableEntityStorage {

        val baseModel = context.getRootModel(GradleDslBaseScriptModel::class.java)?.kotlinDslBaseScriptModel ?: return storage
        val templateClasspath = (baseModel.scriptTemplatesClassPath + baseModel.compileClassPath).map { it.toPath() }
        val templateClasses = getGradleTemplatesNames(context.gradleVersion)

        val definitions = loadDefinitionsFromTemplates(templateClasses, templateClasspath).map {
            BaseScriptDefinition(it)
        }.ifEmpty { sequenceOf(ErrorGradleScriptDefinition()) }.toList().map {
            it.with {
                dependencies(JvmDependency(baseModel.compileClassPath))
                defaultImports(baseModel.implicitImports)
            }
        }

        val builder = storage.toBuilder()
        builder.also { storage ->
            val models = readAction {
                FileEditorManager.getInstance(context.project).allEditors
            }.filter {
                it.file.name.endsWith(".gradle.kts")
            }.map { fileEditor ->
                GradleScriptModel(
                    fileEditor.file, baseModel.compileClassPath.map { it.path }, listOf(), baseModel.implicitImports
                )
            }

            GradleKotlinScriptService.getInstance(context.project).updateStorage(
                storage,
                models,
                definitions,
            )
        }
        return builder.toSnapshot()
    }
}