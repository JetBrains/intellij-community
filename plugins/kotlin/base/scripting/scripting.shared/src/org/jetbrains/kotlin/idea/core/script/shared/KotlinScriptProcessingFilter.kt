// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * This filters can be used to prevent script processing (definition resolution, updating workspace model, caches invalidating).
 * Filter implementations should be permissive - i.e., should prevent highlighting only for files it absolutely knows about,
 * and return true otherwise.
 */
interface KotlinScriptProcessingFilter {
    /**
     * @param virtualFile - file to decide about
     * @return false if this filter disables processing for a given file, true if filter enables processing or can't decide
     */
    fun shouldProcessScript(virtualFile: VirtualFile): Boolean

    companion object {
        val EP_NAME: ProjectExtensionPointName<KotlinScriptProcessingFilter> =
            ProjectExtensionPointName("org.jetbrains.kotlin.kotlinScriptFilter")

        fun shouldProcessScript(project: Project, virtualFile: VirtualFile): Boolean =
            EP_NAME.findFirstSafe(project) { !it.shouldProcessScript(virtualFile) } == null
    }
}