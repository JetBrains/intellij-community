// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.k2.configurations.MainKtsEntityProvider
import org.jetbrains.kotlin.idea.core.script.k2.configurations.scriptEntityProvider
import org.jetbrains.kotlin.idea.core.script.v1.NewScriptFileInfo
import org.jetbrains.kotlin.idea.core.script.v1.kotlinScriptTemplateInfo
import org.jetbrains.kotlin.idea.core.script.v1.loggingReporter
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsFromClasspathDiscoverySource
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.with
import kotlin.script.experimental.dependencies.withTransformedResolvers
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

class MainKtsScriptDefinitionSource(val project: Project) : ScriptDefinitionsSource {

    override val definitions: Sequence<ScriptDefinition>
        get() {
            val discoveredDefinitions = ScriptDefinitionsFromClasspathDiscoverySource(
                listOf(KotlinArtifacts.kotlinMainKts, KotlinArtifacts.kotlinStdlib, KotlinArtifacts.kotlinScriptRuntime, KotlinArtifacts.kotlinReflect),
                defaultJvmScriptingHostConfiguration,
                ::loggingReporter
            ).definitions

            val mainKtsConfigurationProvider = MainKtsEntityProvider.getInstance(project)

            return discoveredDefinitions.map { definition ->
                val compilationConfiguration = definition.compilationConfiguration.withTransformedResolvers {
                    ReportingExternalDependenciesResolver(it, mainKtsConfigurationProvider)
                }.with {
                    ide.dependenciesSources(JvmDependency(KotlinArtifacts.kotlinStdlibSources))
                    ide {
                        kotlinScriptTemplateInfo(NewScriptFileInfo().apply {
                            id = "main-kts"
                            title = ".main.kts"
                            templateName = "Kotlin Script MainKts"
                        })
                        scriptEntityProvider {
                            mainKtsConfigurationProvider
                        }
                    }
                }

                ScriptDefinition.FromConfigurations(
                    compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration] ?: defaultJvmScriptingHostConfiguration,
                    compilationConfiguration,
                    definition.evaluationConfiguration
                ).apply {
                    order = Int.MIN_VALUE
                }
            }
        }
}

