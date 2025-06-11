// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

interface KotlinScriptingSettings {
    fun autoReloadConfigurations(scriptDefinition: ScriptDefinition): Boolean
    fun setAutoReloadConfigurations(scriptDefinition: ScriptDefinition, autoReloadScriptDependencies: Boolean)

    companion object {
        fun getInstance(project: Project): KotlinScriptingSettings = project.service()
    }
}