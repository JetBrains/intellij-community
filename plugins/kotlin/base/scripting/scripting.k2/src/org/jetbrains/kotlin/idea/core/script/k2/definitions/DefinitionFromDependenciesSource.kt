// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import org.jetbrains.kotlin.idea.core.script.k2.settings.ScriptDefinitionSettingsStateComponent
import org.jetbrains.kotlin.idea.core.script.k2.settings.parsedExplicitTemplateClassNames
import org.jetbrains.kotlin.idea.core.script.k2.settings.parsedExplicitTemplateClasspath
import org.jetbrains.kotlin.idea.core.script.shared.definition.loadDefinitionsFromTemplates
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import java.io.File
import java.nio.file.Path
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

interface ScriptDefinitionTemplateEntity : WorkspaceEntity {
    val templateFqns: List<String>
    val classpath: List<String>
}

interface KotlinScriptDefinitionEntitySource : EntitySource

object DefinitionFromDependenciesEntitySource : KotlinScriptDefinitionEntitySource

class DefinitionFromDependenciesSource(val project: Project) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition>
        get() {
            val state = ScriptDefinitionSettingsStateComponent.getInstance(project).state
            val userFqns = state.parsedExplicitTemplateClassNames()
            val userClasspath = state.parsedExplicitTemplateClasspath()
            return project.workspaceModel.currentSnapshot.entities(ScriptDefinitionTemplateEntity::class.java).flatMap {
                loadDefinitions(
                    (userFqns + it.templateFqns).distinct(),
                    (userClasspath + it.classpath).distinct(),
                )
            }
        }

    private fun loadDefinitions(templateFqns: List<String>, classpath: List<String>): List<ScriptDefinition> {
        val hostConfiguration = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
            getEnvironment {
                mapOf(
                    "projectRoot" to (project.basePath ?: project.baseDir.canonicalPath)?.let(::File),
                )
            }
        }

        val newDefinitions = loadDefinitionsFromTemplates(
            templateClassNames = templateFqns,
            templateClasspath = classpath.map { Path.of(it) },
            baseHostConfiguration = hostConfiguration,
        ).map {
            it.apply { order = Int.MIN_VALUE }
        }.toList()

        scriptingDebugLog { "Script definitions found: ${newDefinitions.joinToString()}" }

        return newDefinitions
    }
}

