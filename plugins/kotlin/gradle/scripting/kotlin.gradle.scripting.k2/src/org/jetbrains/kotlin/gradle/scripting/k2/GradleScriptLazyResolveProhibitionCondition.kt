// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.highlighting.KotlinScripDeferredResolutionPolicy
import org.jetbrains.kotlin.idea.core.script.shared.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

private const val GRADLE_KTS = ".gradle.kts"

class GradleScripDeferredResolvePolicy(val project: Project) : KotlinScripDeferredResolutionPolicy {
    override fun shouldDeferResolution(script: VirtualFile): Boolean {
        if (!script.name.endsWith(GRADLE_KTS) || isUnitTestMode()) return false

        // gradle definitions are empty so project import wasn't finished yet
        return project.scriptDefinitionsSourceOfType<GradleScriptDefinitionsSource>()?.definitions.orEmpty().none()
    }
}