// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsFromClasspathDiscoverySource
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

class MainKtsScriptDefinitionSourceK2(val project: Project) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition>
        get() {
            val baseHostConfiguration = defaultJvmScriptingHostConfiguration
            val classPath = listOf(
                KotlinArtifacts.kotlinMainKts,
                KotlinArtifacts.kotlinScriptRuntime,
                KotlinArtifacts.kotlinStdlib,
                KotlinArtifacts.kotlinReflect
            )

            val discoveredDefinitions = ScriptDefinitionsFromClasspathDiscoverySource(
                classPath,
                baseHostConfiguration,
                ::loggingReporter
            ).definitions

            return discoveredDefinitions.map {
                ScriptDefinition.FromConfigurations(
                    it.compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]
                        ?: baseHostConfiguration,
                    it.compilationConfiguration,
                    it.evaluationConfiguration ?: ScriptEvaluationConfiguration.Default
                )
            }
        }
}

