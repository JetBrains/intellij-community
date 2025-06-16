// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionProviderImpl
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

@State(
    name = "ScriptDefinitionSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class ScriptDefinitionPersistentSettings(val project: Project) :
    SimplePersistentStateComponent<DefinitionSettingsState>(DefinitionSettingsState()), KotlinScriptingSettings {

    fun getIndexedSettingsPerDefinition(): Map<String?, IndexedSetting> = state.settings.mapIndexedTo(mutableListOf()) { index, it ->
        it.definitionId to IndexedSetting(index, it)
    }.toMap()

    fun setSettings(settings: List<ScriptDefinitionSetting>) {
        loadState(DefinitionSettingsState(settings.toMutableList()))
        ScriptDefinitionProviderImpl.getInstance(project).notifyDefinitionsChanged()
    }

    override fun autoReloadConfigurations(scriptDefinition: ScriptDefinition): Boolean = true
    override fun setAutoReloadConfigurations(scriptDefinition: ScriptDefinition, autoReloadScriptDependencies: Boolean): Unit = Unit

    companion object {
        fun getInstance(project: Project): ScriptDefinitionPersistentSettings = project.service<KotlinScriptingSettings>() as ScriptDefinitionPersistentSettings
    }
}

class DefinitionSettingsState() : BaseState() {
    constructor(settings: MutableList<ScriptDefinitionSetting>) : this() {
        this.settings = settings
    }

    var settings: MutableList<ScriptDefinitionSetting> by list()
}

open class ScriptDefinitionSetting() : BaseState() {
    constructor(definitionId: String, enabled: Boolean) : this() {
        this.definitionId = definitionId
        this.enabled = enabled
    }

    var definitionId: String? by string()
    var enabled: Boolean by property(true)
}

class IndexedSetting(val index: Int, val setting: ScriptDefinitionSetting)