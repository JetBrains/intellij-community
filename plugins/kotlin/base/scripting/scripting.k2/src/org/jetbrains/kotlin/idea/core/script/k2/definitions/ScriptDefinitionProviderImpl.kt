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
    @Volatile private var _definitions: List<ScriptDefinition> = emptyList()

    override fun getDefinitions(): List<ScriptDefinition> = _definitions

    override val currentDefinitions: Sequence<ScriptDefinition>
        get() {
            if (shouldReloadDefinitions.getAndSet(false)) {
                runCatching {
                    _definitions = SCRIPT_DEFINITIONS_SOURCES.getExtensions(project).flatMap { it.definitions }
                }.onFailure {
                    shouldReloadDefinitions.set(true)
                }
            }

            val settingsByDefinitionId =
                ScriptDefinitionPersistentSettings.getInstance(project).getIndexedSettingsPerDefinition()

            return _definitions
                .filter { settingsByDefinitionId[it.definitionId]?.setting?.enabled != false }
                .sortedBy { settingsByDefinitionId[it.definitionId]?.index ?: it.order }
                .asSequence()
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
