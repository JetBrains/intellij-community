// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.settings

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.definition.canBeSwitchedOff
import org.jetbrains.kotlin.idea.core.script.definition.kotlinScriptTemplate
import org.jetbrains.kotlin.idea.core.script.definitions.ScriptDefinitionProviderImpl
import org.jetbrains.kotlin.idea.core.script.definitions.discoverySource
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ide

/**
 * Everything the settings page shows, as immutable data.
 *
 * The page builds one snapshot from the persisted state and one from the controls. It compares them to
 * find unapplied changes. Reading a snapshot touches no Swing component, so it is safe off the EDT.
 */
internal data class ScriptDefinitionsSnapshot(
    val definitions: List<ScriptDefinitionTableModel>,
    val definitionClasses: List<String>,
    val classpath: List<String>,
)

/** Reads the persisted state and the cached definitions. Runs off the EDT, under a progress. */
internal fun readScriptDefinitionsSnapshot(project: Project): ScriptDefinitionsSnapshot {
    val state = KotlinScriptingSettings.getInstance(project).state
    return ScriptDefinitionsSnapshot(
        definitions = readDefinitionRows(project, state),
        definitionClasses = state.parsedClassNames,
        classpath = state.parsedClasspath,
    )
}

internal fun readDefinitionRows(project: Project, state: KotlinScriptingSettings.State): List<ScriptDefinitionTableModel> =
    ScriptDefinitionProviderImpl.getInstance(project).cachedProvidedDefinitions
        .sortedBy { state.getScriptDefinitionOrder(it) }
        .map { it.toTableModel(state) }

/** Keeps the persisted schema. Both lists serialize with a line break, which the parsers expect. */
internal fun ScriptDefinitionsSnapshot.toSettingsState(): KotlinScriptingSettings.State = KotlinScriptingSettings.State(
    definitions.map { KotlinScriptingSettings.DefinitionSetting(it.name, it.id, it.isEnabled) },
    definitionClasses.joinToString("\n"),
    classpath.joinToString("\n"),
)

private fun ScriptDefinition.toTableModel(state: KotlinScriptingSettings.State): ScriptDefinitionTableModel {
    val title = compilationConfiguration[ScriptCompilationConfiguration.ide.kotlinScriptTemplate]?.title?.takeIf { it.isNotBlank() }
    val fromConfigurations = this as? ScriptDefinition.FromConfigurationsBase

    return ScriptDefinitionTableModel(
        id = definitionId,
        name = name,
        pattern = title ?: ".$fileExtension",
        filePattern = fromConfigurations?.filePathPattern ?: fromConfigurations?.fileNamePattern,
        source = compilationConfiguration[ScriptCompilationConfiguration.ide.discoverySource],
        canBeSwitchedOff = compilationConfiguration[ScriptCompilationConfiguration.ide.canBeSwitchedOff] ?: true,
        isEnabled = state.isScriptDefinitionEnabled(this),
    )
}
