// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface KotlinScriptReloadActionAvailability {
    companion object {
        val EP_NAME = ProjectExtensionPointName<KotlinScriptReloadActionAvailability>("org.jetbrains.kotlin.scriptReloadActionAvailability")

        fun showReloadAction(project: Project, script: VirtualFile): Boolean = EP_NAME.getExtensions(project).all { it.showReloadAction(script) }
    }

    fun showReloadAction(script: VirtualFile): Boolean = true
}