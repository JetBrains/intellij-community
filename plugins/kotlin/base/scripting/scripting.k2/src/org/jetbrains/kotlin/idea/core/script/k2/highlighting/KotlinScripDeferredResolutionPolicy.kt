// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.highlighting

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Regular scripts (.kts) do not support external dependencies, so there is no need to postpone resolving.
 * However, certain plugins (e.g., Gradle) may apply their own approach to scripting analysis, which occasionally doesn't imply lazy evaluation.
 */
interface KotlinScripDeferredResolutionPolicy {
    companion object {
        val EP_NAME: ProjectExtensionPointName<KotlinScripDeferredResolutionPolicy> =
          ProjectExtensionPointName<KotlinScripDeferredResolutionPolicy>("org.jetbrains.kotlin.kotlinScripDeferredResolutionPolicy")

        fun shouldDeferResolution(project: Project, script: VirtualFile): Boolean =
            EP_NAME.getExtensions(project).any { it.shouldDeferResolution(script) }
    }

    fun shouldDeferResolution(script: VirtualFile): Boolean = false
}