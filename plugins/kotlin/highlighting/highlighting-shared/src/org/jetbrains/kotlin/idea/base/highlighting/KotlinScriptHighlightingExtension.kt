// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtFile

interface KotlinScriptHighlightingExtension {
    fun shouldHighlightScript(file: KtFile): Boolean

    companion object {
        val EP_NAME: ProjectExtensionPointName<KotlinScriptHighlightingExtension> =
            ProjectExtensionPointName("org.jetbrains.kotlin.scriptHighlightingExtension")

        /**
         * Regular scripts (.kts) do not support external dependencies, so there is no need to postpone resolving.
         * However, certain plugins (e.g., Gradle) may apply their own approach to scripting analysis, which occasionally doesn't imply lazy evaluation.
         */
        fun shouldHighlightScript(project: Project, file: KtFile): Boolean {
            return EP_NAME.getExtensions(project).all { it.shouldHighlightScript(file) }
        }
    }
}