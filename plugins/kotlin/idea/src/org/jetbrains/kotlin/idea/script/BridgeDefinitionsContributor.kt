// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.k2.loggingReporter
import org.jetbrains.kotlin.idea.core.script.loadDefinitionsFromTemplates
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsFromClasspathDiscoverySource
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

class BridgeScriptDefinitionsContributor(private val project: Project) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition>
        get() = ScriptDefinitionsProvider.EP_NAME.getExtensionList(project).asSequence().flatMap { provider ->
            val explicitClasses = provider.getDefinitionClasses().toList()
            val classPath = provider.getDefinitionsClassPath().toList()
            val baseHostConfiguration = defaultJvmScriptingHostConfiguration
            // TODO: rewrite load and discovery to return kotlin.script.experimental.host.ScriptDefinition to avoid unnecessary conversions
            val explicitDefinitions = if (explicitClasses.isEmpty())
                emptySequence()
            else
                loadDefinitionsFromTemplates(explicitClasses, classPath, baseHostConfiguration).asSequence()

            val discoveredDefinitions = if (provider.useDiscovery())
                ScriptDefinitionsFromClasspathDiscoverySource(
                    classPath,
                    baseHostConfiguration,
                    ::loggingReporter
                ).definitions
            else
                emptySequence()

            val loadedDefinitions = (explicitDefinitions + discoveredDefinitions).map {
                kotlin.script.experimental.host.ScriptDefinition(
                    it.compilationConfiguration,
                    it.evaluationConfiguration ?: ScriptEvaluationConfiguration.Default,
                )
            }.toList()

            provider.provideDefinitions(baseHostConfiguration, loadedDefinitions).map {
                ScriptDefinition.FromNewDefinition(baseHostConfiguration, it)
            }.asSequence()
        }
}


