// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.KotlinScriptReloadActionAvailability
import org.jetbrains.kotlin.idea.core.script.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource

class GradleScriptReloadActionAvailability(val project: Project) : KotlinScriptReloadActionAvailability {
    override fun showReloadAction(script: VirtualFile): Boolean {
        val definition = ScriptDefinitionProvider.getInstance(project)?.findDefinition(VirtualFileScriptSource(script))
        val gradleDefinitions = project.scriptDefinitionsSourceOfType<GradleScriptDefinitionsSource>()?.definitions ?: return false

        return !gradleDefinitions.contains(definition)
    }
}