// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE")

package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.shared.definition.kotlinScriptTemplate
import java.nio.file.Path
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.JvmDependency

class MainKtsScriptDefinitionsProvider(val project: Project) : ScriptDefinitionsProvider {
    override val id: String = "MainKts"

    override fun getTemplateClasspath(): List<Path> = listOf(
        KotlinArtifacts.kotlinMainKtsPath,
        KotlinArtifacts.kotlinStdlibPath,
        KotlinArtifacts.kotlinScriptRuntimePath,
        KotlinArtifacts.kotlinReflectPath,
    )

    override fun useDiscovery(): Boolean = true

    override fun provideDefinitions(
        baseHostConfiguration: ScriptingHostConfiguration,
        loadedScriptDefinitions: List<ScriptDefinition>,
    ): List<ScriptDefinition> = loadedScriptDefinitions.map { definition ->
        val compilationConfiguration = definition.compilationConfiguration.with {
            ide.dependenciesSources(JvmDependency(KotlinArtifacts.kotlinStdlibSources))
            kotlinScriptTemplate {
                id = "main-kts"
                title = ".main.kts"
                templateName = "Kotlin Script MainKts"
                @Suppress("HardCodedStringLiteral")
                description = "Standalone script, supports @file:DependsOn for external library imports."
            }
        }

        ScriptDefinition(compilationConfiguration, definition.evaluationConfiguration)
    }
}
