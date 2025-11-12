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
class ScriptDefinitionPersistentSettings(val project: Project) :
  SerializablePersistentStateComponent<ScriptDefinitionPersistentSettings.State>(State()), KotlinScriptingSettings {

    fun setSettings(settings: List<ScriptDefinitionSetting>) {
        updateState {
            it.copy(settings = settings)
        }
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }

    override fun isScriptDefinitionEnabled(scriptDefinition: ScriptDefinition): Boolean {
        return state.settings.firstOrNull { it.matches(scriptDefinition) }?.enabled ?: true
    }

    override fun getScriptDefinitionOrder(scriptDefinition: ScriptDefinition): Int {
        val index = state.settings.indexOfFirst { it.matches(scriptDefinition) }
        return if (index == -1) scriptDefinition.order else index
    }

    companion object {
        fun getInstance(project: Project): ScriptDefinitionPersistentSettings =
            KotlinScriptingSettings.getInstance(project) as ScriptDefinitionPersistentSettings
    }

    data class State(
        @JvmField @XCollection(
            propertyElementName = "settings", elementName = "definition"
        ) val settings: List<ScriptDefinitionSetting> = listOf()
    )

    @Tag("definition")
    class ScriptDefinitionSetting(
        @JvmField @Attribute val name: String? = null,
        @JvmField @Attribute val definitionId: String? = null,
        @JvmField @Attribute val enabled: Boolean = true
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ScriptDefinitionSetting

            if (name != other.name) return false
            if (definitionId != other.definitionId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name?.hashCode() ?: 0
            result = 31 * result + (definitionId?.hashCode() ?: 0)
            return result
        }

        fun matches(definition: ScriptDefinition): Boolean = name == definition.name && definitionId == definition.definitionId
    }
}
