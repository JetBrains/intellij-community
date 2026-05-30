// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.settings

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.core.script.v1.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

@State(
    name = "ScriptDefinitionSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
internal class ScriptDefinitionSettingsStateComponent(val project: Project) :
    SerializablePersistentStateComponent<ScriptDefinitionSettingsStateComponent.State>(State()), KotlinScriptingSettings {

    fun update(updateFunction: (ScriptDefinitionSettingsStateComponent.State) -> ScriptDefinitionSettingsStateComponent.State) {
        val before = state
        val after = updateState(updateFunction)
        if (before != after) {
            ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
        }
    }

    override fun isScriptDefinitionEnabled(scriptDefinition: ScriptDefinition): Boolean = state.isScriptDefinitionEnabled(scriptDefinition)

    override fun getScriptDefinitionOrder(scriptDefinition: ScriptDefinition): Int = state.getScriptDefinitionOrder(scriptDefinition)

    companion object {
        fun getInstance(project: Project): ScriptDefinitionSettingsStateComponent =
            KotlinScriptingSettings.getInstance(project) as ScriptDefinitionSettingsStateComponent
    }

    internal data class State(
        @JvmField @XCollection(
            propertyElementName = "settings", elementName = "definition"
        ) val settings: List<DefinitionSetting> = listOf(),
        @JvmField @Attribute val explicitTemplateClassNames: String = "",
        @JvmField @Attribute val explicitTemplateClasspath: String = "",
    )

    @Tag("definition")
    internal data class DefinitionSetting(
        @JvmField @Attribute val name: String? = null,
        @JvmField @Attribute val definitionId: String? = null,
        @JvmField @Attribute val enabled: Boolean = true
    ) {
        fun matches(definition: ScriptDefinition): Boolean = name == definition.name && definitionId == definition.definitionId
    }
}

internal fun ScriptDefinitionSettingsStateComponent.State.getScriptDefinitionOrder(scriptDefinition: ScriptDefinition): Int {
    val index = settings.indexOfFirst { it.matches(scriptDefinition) }
    return if (index == -1) scriptDefinition.order else index
}

internal fun ScriptDefinitionSettingsStateComponent.State.isScriptDefinitionEnabled(scriptDefinition: ScriptDefinition): Boolean =
    settings.firstOrNull { it.matches(scriptDefinition) }?.enabled ?: true

internal val explicitTemplateDelimiters: Array<String> = arrayOf(":", ",", ";", "\n", "\t", " ")

internal fun parseExplicitTemplateInput(text: String): List<String> =
    text.split(*explicitTemplateDelimiters).map { it.trim() }.filter { it.isNotEmpty() }

internal val ScriptDefinitionSettingsStateComponent.State.parsedClassNames: List<String>
    get() = parseExplicitTemplateInput(explicitTemplateClassNames)

internal val ScriptDefinitionSettingsStateComponent.State.parsedClasspath: List<String>
    get() = parseExplicitTemplateInput(explicitTemplateClasspath)
