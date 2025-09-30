// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.kotlin.caches.project.cacheByClass
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.script.k2.settings.ScriptDefinitionPersistentSettings
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import kotlin.script.experimental.api.SourceCode


@Service(Service.Level.PROJECT)
class ScriptDefinitionsModificationTracker : SimpleModificationTracker() {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): ScriptDefinitionsModificationTracker = project.service()
    }
}

class ScriptDefinitionProviderImpl(val project: Project) : ScriptDefinitionProvider {
    override val currentDefinitions: Sequence<ScriptDefinition>
        get() = computeOrGetDefinitions(project)

    override fun isScript(script: SourceCode): Boolean = findDefinition(script) != null

    override fun getKnownFilenameExtensions(): Sequence<String> = (currentDefinitions + getDefaultDefinition()).map { it.fileExtension }.distinct()

    override fun findDefinition(script: SourceCode): ScriptDefinition? {
        val locationId = script.locationId ?: return null
        if (nonScriptFilenameSuffixes.any { locationId.endsWith(it, ignoreCase = true) }) return null

        return currentDefinitions.firstOrNull { it.isScript(script) }
    }

    override fun getDefaultDefinition(): ScriptDefinition = project.defaultDefinition

    companion object {
        private fun computeOrGetDefinitions(project: Project): Sequence<ScriptDefinition> = project.cacheByClass(
            ScriptDefinitionProviderImpl::class.java,
            ScriptDefinitionsModificationTracker.getInstance(project)
        ) {
            val settingsProvider = ScriptDefinitionPersistentSettings.getInstance(project)

            SCRIPT_DEFINITIONS_SOURCES.getExtensions(project).flatMap { it.definitions }
                .filter { settingsProvider.isScriptDefinitionEnabled(it) }
                .sortedBy { settingsProvider.getScriptDefinitionOrder(it) }
                .asSequence()
        }

        private val nonScriptFilenameSuffixes: Set<String> = setOf(".${KotlinFileType.EXTENSION}", ".${JavaFileType.DEFAULT_EXTENSION}")

        fun getInstance(project: Project): ScriptDefinitionProviderImpl =
            project.service<ScriptDefinitionProvider>() as ScriptDefinitionProviderImpl
    }
}
