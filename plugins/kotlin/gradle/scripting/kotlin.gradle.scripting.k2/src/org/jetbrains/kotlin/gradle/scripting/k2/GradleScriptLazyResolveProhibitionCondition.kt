// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.KotlinScriptLazyResolveProhibitionCondition
import org.jetbrains.kotlin.idea.core.script.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource

private const val GRADLE_KTS = ".gradle.kts"

class GradleScriptLazyResolveProhibitionCondition(val project: Project) : KotlinScriptLazyResolveProhibitionCondition {
    override fun shouldPostponeResolution(script: VirtualFile): Boolean {
        val gradleDefinitionIds =
            project.scriptDefinitionsSourceOfType<GradleScriptDefinitionsSource>()?.definitions?.map { it.definitionId }?.toSet()
                ?: emptySet()

        // gradle definitions are empty so project import wasn't finished yet
        if (gradleDefinitionIds.isEmpty() && script.name.endsWith(GRADLE_KTS)) return true

        val definition = findScriptDefinition(project, VirtualFileScriptSource(script)).definitionId

        return gradleDefinitionIds.contains(definition)
    }
}