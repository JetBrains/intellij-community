// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.k2.settings.ScriptDefinitionPersistentSettings
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.v1.IdeScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import java.util.concurrent.atomic.AtomicBoolean

class ScriptDefinitionProviderImpl(val project: Project) : IdeScriptDefinitionProvider() {
    private val shouldReloadDefinitions = AtomicBoolean(true)

    @Volatile
    private var definitions: List<ScriptDefinition> = emptyList()

    override fun getDefinitions(): List<ScriptDefinition> = currentDefinitions.toList()

    override val currentDefinitions: Sequence<ScriptDefinition>
        get() {
            if (shouldReloadDefinitions.get()) {
                synchronized(this) {
                    if (shouldReloadDefinitions.get()) {
                        val loaded = SCRIPT_DEFINITIONS_SOURCES
                            .getExtensions(project)
                            .flatMap { it.definitions }

                        definitions = loaded
                        shouldReloadDefinitions.set(false)
                    }
                }
            }

            val settingsProvider = ScriptDefinitionPersistentSettings.getInstance(project)

            return definitions
                .asSequence()
                .filter { settingsProvider.isScriptDefinitionEnabled(it) }
                .sortedBy { settingsProvider.getScriptDefinitionOrder(it) }
        }

    override fun getDefaultDefinition(): ScriptDefinition = project.defaultDefinition

    fun notifyDefinitionsChanged() {
        shouldReloadDefinitions.set(true)
        clearCache()
    }

    companion object {
        fun getInstance(project: Project): ScriptDefinitionProviderImpl =
            project.service<ScriptDefinitionProvider>() as ScriptDefinitionProviderImpl
    }
}
