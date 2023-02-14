// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import kotlin.script.experimental.api.ScriptDiagnostic

class IdeScriptReportSink(val project: Project) : ScriptReportSink {
    override fun attachReports(scriptFile: VirtualFile, reports: List<ScriptDiagnostic>) {
        if (getReports(scriptFile) == reports) return

        // TODO: persist errors between launches?
        scriptFile.scriptDiagnostics = reports
    }

    companion object {
        fun getReports(file: VirtualFile): List<ScriptDiagnostic> {
            return file.scriptDiagnostics ?: emptyList()
        }

        fun getReports(file: KtFile): List<ScriptDiagnostic> {
            return file.originalFile.virtualFile?.scriptDiagnostics ?: emptyList()
        }

        private var VirtualFile.scriptDiagnostics: List<ScriptDiagnostic>? by UserDataProperty(Key.create("KOTLIN_SCRIPT_DIAGNOSTICS"))
    }
}