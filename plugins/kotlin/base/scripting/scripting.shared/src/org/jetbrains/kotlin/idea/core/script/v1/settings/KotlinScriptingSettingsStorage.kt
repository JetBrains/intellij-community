// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.v1.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

@ApiStatus.Internal
interface KotlinScriptingSettingsStorage {
    fun autoReloadConfigurations(scriptDefinition: ScriptDefinition): Boolean
    fun setAutoReloadConfigurations(scriptDefinition: ScriptDefinition, autoReloadScriptDependencies: Boolean)
    fun setOrder(scriptDefinition: ScriptDefinition, order: Int)
    fun isScriptDefinitionEnabled(scriptDefinition: ScriptDefinition): Boolean
    fun getScriptDefinitionOrder(scriptDefinition: ScriptDefinition): Int

    companion object {
        fun getInstance(project: Project): KotlinScriptingSettingsStorage = project.service()
    }
}

@ApiStatus.Internal
@Deprecated("marked for deletion")
abstract class KotlinScriptingSettings : KotlinScriptingSettingsStorage {

}