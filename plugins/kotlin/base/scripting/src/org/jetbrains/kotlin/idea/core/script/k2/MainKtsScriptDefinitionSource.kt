// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsFromClasspathDiscoverySource
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import kotlin.script.experimental.api.*
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

            return discoveredDefinitions.map { definition ->
                val compilationConfiguration = definition.compilationConfiguration.withTransformedResolvers {
                    ReportingExternalDependenciesResolver(it, DependencyResolutionService.getInstance(project))
                }.with {
                    ide.dependenciesSources(JvmDependency(KotlinArtifacts.kotlinStdlibSources))
                    ide {
                        kotlinScriptTemplateInfo(NewScriptFileInfo().apply {
                            id = "main-kts"
                            title = ".main.kts"
                            templateName = "Kotlin Script MainKts"
                        })
                        configurationResolverDelegate {
                            MainKtsScriptConfigurationProvider.getInstance(project)
                        }
                        scriptWorkspaceModelManagerDelegate {
                            MainKtsScriptConfigurationProvider.getInstance(project)
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

fun loggingReporter(severity: ScriptDiagnostic.Severity, message: String) {
    val log = Logger.getInstance("ScriptDefinitionsProviders")
    when (severity) {
        ScriptDiagnostic.Severity.FATAL, ScriptDiagnostic.Severity.ERROR -> log.error(message)

        ScriptDiagnostic.Severity.WARNING, ScriptDiagnostic.Severity.INFO -> log.info(message)

        else -> {}
    }
}
