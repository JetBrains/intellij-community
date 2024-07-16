// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.scripting.definitions.LazyScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

/**
 * Holds uploaded cache definitions.
 * Returns default definition if update did not happen.
 */
class K2ScriptDefinitionProvider(val project: Project) : LazyScriptDefinitionProvider() {
    private val _definitions: AtomicReference<List<ScriptDefinition>> = AtomicReference()

    public override val currentDefinitions: Sequence<ScriptDefinition>
        get() = _definitions.get()?.takeIf { it.isNotEmpty() }?.asSequence() ?: sequenceOf(getDefaultDefinition())

    fun reloadDefinitions() {
        _definitions.set(SCRIPT_DEFINITIONS_SOURCES.getExtensions(project).flatMap { it.definitions })
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