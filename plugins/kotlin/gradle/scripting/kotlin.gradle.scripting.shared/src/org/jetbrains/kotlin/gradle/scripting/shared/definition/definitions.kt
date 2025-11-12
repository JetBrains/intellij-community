// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared.definition

import org.jetbrains.kotlin.gradle.scripting.shared.KotlinGradleScriptingBundle
import org.jetbrains.kotlin.scripting.definitions.ScriptCompilationConfigurationFromDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.templates.standard.ScriptTemplateWithArgs

open class GradleScriptDefinition(
    private val initialConfiguration: ScriptCompilationConfiguration,
    override val hostConfiguration: ScriptingHostConfiguration,
    override val evaluationConfiguration: ScriptEvaluationConfiguration?,
    override val defaultCompilerOptions: Iterable<String> = emptyList(),
    private val _externalProjectPath: String? = null,
) : ScriptDefinition.FromConfigurationsBase() {

    init {
        order = Int.MIN_VALUE
    }

    override val canDefinitionBeSwitchedOff: Boolean = false

    override val compilationConfiguration: ScriptCompilationConfiguration by lazy {
        initialConfiguration
            .with(configurationBody)
            .with {
                gradle {
                    externalProjectPath(_externalProjectPath)
                }
                ide {
                    acceptedLocations.put(listOf(ScriptAcceptedLocation.Project))
                }
            }
    }

    fun with(body: ScriptCompilationConfiguration.Builder.() -> Unit): GradleScriptDefinition {
        val newConfiguration = ScriptCompilationConfiguration(compilationConfiguration, body = body)
        return GradleScriptDefinition(
            newConfiguration, hostConfiguration, evaluationConfiguration, defaultCompilerOptions, _externalProjectPath
        )
    }

    protected open val configurationBody: ScriptCompilationConfiguration.Builder.() -> Unit = {}
}

class BaseScriptDefinition(private val definition: ScriptDefinition) : GradleScriptDefinition(
    definition.compilationConfiguration,
    definition.hostConfiguration,
    definition.evaluationConfiguration,
    definition.defaultCompilerOptions,
) {
    override val configurationBody: ScriptCompilationConfiguration.Builder.() -> Unit = {
        displayName("${definition.name} (Base)")
    }

    override val definitionId: String = "${super.definitionId}_base"
}

class LegacyGradleScriptDefinition(
    private val _legacyDefinition: KotlinScriptDefinitionFromAnnotatedTemplate,
    hostConfiguration: ScriptingHostConfiguration,
    evaluationConfiguration: ScriptEvaluationConfiguration?,
    defaultCompilerOptions: Iterable<String>,
    externalProjectPath: String? = null
) : GradleScriptDefinition(
    ScriptCompilationConfigurationFromDefinition(
        hostConfiguration, _legacyDefinition
    ), hostConfiguration, evaluationConfiguration, defaultCompilerOptions, externalProjectPath
) {
    override val configurationBody: ScriptCompilationConfiguration.Builder.() -> Unit = {
        @Suppress("DEPRECATION_ERROR") fileNamePattern.put(_legacyDefinition.scriptFilePattern.pattern)
    }
}

class ErrorGradleScriptDefinition : GradleScriptDefinition(
    ScriptCompilationConfiguration.Default,
    ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration),
    ScriptEvaluationConfiguration {
        hostConfiguration(ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration))
    }) {

    override val configurationBody: ScriptCompilationConfiguration.Builder.() -> Unit = {
        fileExtension("gradle.kts")
        baseClass(KotlinType(ScriptTemplateWithArgs::class))
        displayName(KotlinGradleScriptingBundle.message("text.default.kotlin.gradle.script"))
    }
}