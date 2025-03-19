// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "ScriptDefinitionSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class ScriptDefinitionPersistentSettings(val project: Project) :
    SimplePersistentStateComponent<DefinitionSettingsState>(DefinitionSettingsState()) {

    fun getIndexedSettingsPerDefinition(): Map<String?, IndexedSetting> = state.settings.mapIndexedTo(mutableListOf()) { index, it ->
        it.definitionId to IndexedSetting(index, it)
    }.toMap()

    fun setSettings(settings: List<ScriptDefinitionSetting>) {
        loadState(DefinitionSettingsState(settings.toMutableList()))
        K2ScriptDefinitionProvider.getInstance(project).reloadDefinitionsFromSources()
    }

    companion object {
        fun getInstance(project: Project): ScriptDefinitionPersistentSettings = project.service()
    }
}

class DefinitionSettingsState() : BaseState() {
    constructor(settings: MutableList<ScriptDefinitionSetting>) : this() {
        this.settings = settings
    }

    var settings by list<ScriptDefinitionSetting>()
}

open class ScriptDefinitionSetting() : BaseState() {
    constructor(definitionId: String, enabled: Boolean) : this() {
        this.definitionId = definitionId
        this.enabled = enabled
    }

    var definitionId by string()
    var enabled by property(true)
}

class IndexedSetting(val index: Int, val setting: ScriptDefinitionSetting)