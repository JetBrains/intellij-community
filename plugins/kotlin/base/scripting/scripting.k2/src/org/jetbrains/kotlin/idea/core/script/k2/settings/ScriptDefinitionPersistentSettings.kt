// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionProviderImpl
import org.jetbrains.kotlin.idea.core.script.v1.settings.KotlinScriptingSettingsStorage
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

@State(
    name = "ScriptDefinitionSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class ScriptDefinitionPersistentSettings(val project: Project) :
    SerializablePersistentStateComponent<ScriptDefinitionPersistentSettings.State>(State()), KotlinScriptingSettingsStorage {

    fun getIndexedSettingsPerDefinition(): Map<String?, IndexedSetting> = state.settings.mapIndexedTo(mutableListOf()) { index, it ->
        it.definitionId to IndexedSetting(index, it)
    }.toMap()

    fun setSettings(settings: List<ScriptDefinitionSetting>) {
        updateState {
            it.copy(settings = settings)
        }
        ScriptDefinitionProviderImpl.getInstance(project).notifyDefinitionsChanged()
    }

    override fun autoReloadConfigurations(scriptDefinition: ScriptDefinition): Boolean = true
    override fun setAutoReloadConfigurations(scriptDefinition: ScriptDefinition, autoReloadScriptDependencies: Boolean): Unit = Unit

    override fun setOrder(scriptDefinition: ScriptDefinition, order: Int) {
        val settings = state.settings.toMutableList()
        if (settings[order].definitionId == scriptDefinition.definitionId) return

        settings.removeIf { it.definitionId == scriptDefinition.definitionId }
        settings[order] = ScriptDefinitionSetting(scriptDefinition.definitionId, true)

        updateState {
            it.copy(settings = settings)
        }
    }

    override fun isScriptDefinitionEnabled(scriptDefinition: ScriptDefinition): Boolean {
        return state.settings.firstOrNull { it.definitionId == scriptDefinition.definitionId }?.enabled ?: true
    }

    override fun getScriptDefinitionOrder(scriptDefinition: ScriptDefinition): Int {
        val order = state.settings.indexOfFirst { it.definitionId == scriptDefinition.definitionId }
        return if (order == -1) Integer.MAX_VALUE else order
    }

    companion object {
        fun getInstance(project: Project): ScriptDefinitionPersistentSettings =
            project.service<KotlinScriptingSettingsStorage>() as ScriptDefinitionPersistentSettings
    }

    data class State(
        @Attribute @JvmField var settings: List<ScriptDefinitionSetting> = listOf()
    )

    data class ScriptDefinitionSetting(
        @Attribute @JvmField val definitionId: String? = null,
        @Attribute @JvmField val enabled: Boolean = true,
    )

    class IndexedSetting(val index: Int, val setting: ScriptDefinitionSetting)
}
