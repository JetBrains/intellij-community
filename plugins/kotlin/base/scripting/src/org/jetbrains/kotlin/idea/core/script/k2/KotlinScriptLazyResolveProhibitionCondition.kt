// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Regular scripts (.kts) do not support external dependencies, so there is no need to postpone resolving.
 * However, certain plugins (e.g., Gradle) may apply their own approach to scripting analysis, which occasionally doesn't imply lazy evaluation.
 */
interface KotlinScriptLazyResolveProhibitionCondition {
    companion object {
        val EP_NAME: ProjectExtensionPointName<KotlinScriptLazyResolveProhibitionCondition> =
            ProjectExtensionPointName<KotlinScriptLazyResolveProhibitionCondition>("org.jetbrains.kotlin.kotlinScriptLazyResolveProhibitionCondition")

        fun shouldPostponeResolution(project: Project, script: VirtualFile): Boolean =
            EP_NAME.getExtensions(project).any { it.shouldPostponeResolution(script) }
    }

    fun shouldPostponeResolution(script: VirtualFile): Boolean = false
}