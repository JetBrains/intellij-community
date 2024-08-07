// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.k2.K2ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.fileNamePattern
import kotlin.script.experimental.api.with

class GradleScriptDefinitionsSource(val project: Project) : ScriptDefinitionsSource {
    private val _definitions: AtomicReference<List<ScriptDefinition>> = AtomicReference(listOf())

    override val definitions: Sequence<ScriptDefinition>
        get() = _definitions.get().asSequence()

    /**
     * Force-wrap legacy definitions into `ScriptDefinition.FromConfigurations` when updating.
     */
    fun updateDefinitions(templateDefinitions: List<ScriptDefinition>) {
        val definitionsFromConfigurations = templateDefinitions.map { definition ->
            val configuration = definition.compilationConfiguration.with {
                definition.asLegacyOrNull<KotlinScriptDefinitionFromAnnotatedTemplate>()?.let {
                    @Suppress("DEPRECATION_ERROR")
                    fileNamePattern(it.scriptFilePattern.pattern)
                }
            }

            ScriptDefinition.FromConfigurations(
                definition.hostConfiguration,
                configuration,
                definition.evaluationConfiguration,
                definition.defaultCompilerOptions
            )
        }
        _definitions.set(definitionsFromConfigurations)
        K2ScriptDefinitionProvider.getInstance(project).reloadDefinitionsFromSources()
    }

    companion object {
        fun getInstance(project: Project): GradleScriptDefinitionsSource? =
            SCRIPT_DEFINITIONS_SOURCES.getExtensions(project)
                .filterIsInstance<GradleScriptDefinitionsSource>().firstOrNull()
                .safeAs<GradleScriptDefinitionsSource>()
    }
}