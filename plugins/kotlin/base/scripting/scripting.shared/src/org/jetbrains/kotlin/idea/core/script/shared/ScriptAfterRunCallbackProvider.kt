// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared

import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ScriptAfterRunCallbackProvider {
    fun provide(scriptPath: String): ProgramRunner.Callback?

    companion object {
        val EP_NAME: ProjectExtensionPointName<ScriptAfterRunCallbackProvider> =
          ProjectExtensionPointName<ScriptAfterRunCallbackProvider>("org.jetbrains.kotlin.scriptAfterRunCallbackProvider")

        fun getCallback(project: Project, scriptPath: String): ProgramRunner.Callback? =
            EP_NAME.getExtensions(project).firstNotNullOfOrNull { it.provide(scriptPath) }
    }
}