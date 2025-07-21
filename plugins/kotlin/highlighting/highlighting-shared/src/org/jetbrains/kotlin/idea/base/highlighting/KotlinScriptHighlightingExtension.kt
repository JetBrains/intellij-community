// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtFile

interface KotlinScriptHighlightingExtension {
    fun shouldHighlightScript(file: KtFile): Boolean

    companion object {
        val EP_NAME: ProjectExtensionPointName<KotlinScriptHighlightingExtension> =
            ProjectExtensionPointName<KotlinScriptHighlightingExtension>("org.jetbrains.kotlin.scriptHighlightingExtension")

        fun shouldHighlightScript(project: Project, file: KtFile): Boolean {
            return EP_NAME.getExtensions(project).any { it.shouldHighlightScript(file) }
        }
    }
}