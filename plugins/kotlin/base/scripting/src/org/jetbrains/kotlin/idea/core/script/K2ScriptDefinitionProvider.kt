// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.scripting.definitions.LazyScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.filePathPattern
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

/**
 * Holds uploaded cache definitions.
 * Returns default definition if update did not happen.
 * Force-wrap legacy definitions into `ScriptDefinition.FromConfigurations` when updating.
 */
class K2ScriptDefinitionProvider(val project: Project) : LazyScriptDefinitionProvider() {
    private val _definitions: AtomicReference<List<ScriptDefinition>> = AtomicReference()

    public override val currentDefinitions: Sequence<ScriptDefinition>
        get() = _definitions.get()?.takeIf { it.isNotEmpty() }?.asSequence() ?: sequenceOf(getDefaultDefinition())

    fun updateDefinitions(templateDefinitions: List<ScriptDefinition>) {
        val definitionsFromConfigurations = templateDefinitions.map { definition ->
            val configuration = definition.compilationConfiguration.with {
                definition.asLegacyOrNull<KotlinScriptDefinitionFromAnnotatedTemplate>()?.let {
                    // remove when fix pattern processing on compiler side
                    filePathPattern(it.scriptFilePattern.pattern)
                }
            }

            object : ScriptDefinition.FromConfigurations(
                definition.hostConfiguration,
                configuration,
                definition.evaluationConfiguration,
                definition.defaultCompilerOptions
            ) {
                // remove when fix pattern processing on compiler side
                override fun isScript(script: SourceCode): Boolean {
                    val extension = ".$fileExtension"
                    val location = script.locationId ?: return false
                    val name = script.name ?: location
                    return name.endsWith(extension) && filePathPattern?.let { Regex(it).matches(name) } != false
                }
            }
        }
        _definitions.set(definitionsFromConfigurations)
        clearCache()
    }

    override fun getDefaultDefinition(): ScriptDefinition =
        ScriptDefinition.FromConfigurations(defaultJvmScriptingHostConfiguration, ScriptCompilationConfiguration.Default, null)

    companion object {
        fun getInstance(project: Project): K2ScriptDefinitionProvider =
            project.service<ScriptDefinitionProvider>() as K2ScriptDefinitionProvider

        fun getInstanceIfCreated(project: Project): K2ScriptDefinitionProvider? =
            project.serviceIfCreated<ScriptDefinitionProvider>() as? K2ScriptDefinitionProvider
    }
}