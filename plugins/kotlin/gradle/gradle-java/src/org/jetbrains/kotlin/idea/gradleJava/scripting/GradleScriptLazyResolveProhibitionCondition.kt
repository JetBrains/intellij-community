// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.KotlinScriptLazyResolveProhibitionCondition
import org.jetbrains.kotlin.idea.core.script.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource

private const val GRADLE_KTS = ".gradle.kts"

class GradleScriptLazyResolveProhibitionCondition(val project: Project) : KotlinScriptLazyResolveProhibitionCondition {
    override fun prohibitLazyResolve(script: VirtualFile): Boolean {
        val gradleDefinitions =
            project.scriptDefinitionsSourceOfType<GradleScriptDefinitionsSource>()?.definitions?.map { it.definitionId }?.toSet()
                ?: emptySet()

        // gradle definitions are empty so project import wasn't finished yet
        if (gradleDefinitions.isEmpty() && script.name.contains(GRADLE_KTS)) {
            return true
        }

        val definition = ScriptDefinitionProvider.getInstance(project)?.findDefinition(VirtualFileScriptSource(script))?.definitionId

        return gradleDefinitions.contains(definition)
    }
}