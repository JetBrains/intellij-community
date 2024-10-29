// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.defaultDefinition
import org.jetbrains.kotlin.scripting.definitions.LazyScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.*

/**
 * Holds uploaded cache definitions.
 * Returns default definition if update did not happen.
 */
class K2ScriptDefinitionProvider(val project: Project) : LazyScriptDefinitionProvider() {
    private val allDefinitions: AtomicReference<List<ScriptDefinition>> = AtomicReference(emptyList())

    init {
        reloadDefinitionsFromSources()
    }

    fun getAllDefinitions(): List<ScriptDefinition> = allDefinitions.get()

    public override val currentDefinitions: Sequence<ScriptDefinition>
        get() {
            val settingsByDefinitionId =
                ScriptDefinitionPersistentSettings.getInstance(project).getIndexedSettingsPerDefinition()

            return allDefinitions.get()
                .filter { settingsByDefinitionId[it.definitionId]?.setting?.enabled != false }
                .sortedBy { settingsByDefinitionId[it.definitionId]?.index ?: it.order }
                .asSequence()
        }

    fun reloadDefinitionsFromSources() {
        val scriptDefinitions = SCRIPT_DEFINITIONS_SOURCES.getExtensions(project)
            .flatMap { it.definitions }

        allDefinitions.set(scriptDefinitions)
        clearCache()
    }

    override fun getDefaultDefinition(): ScriptDefinition = project.defaultDefinition

    companion object {
        fun getInstance(project: Project): K2ScriptDefinitionProvider =
            project.service<ScriptDefinitionProvider>() as K2ScriptDefinitionProvider
    }
}