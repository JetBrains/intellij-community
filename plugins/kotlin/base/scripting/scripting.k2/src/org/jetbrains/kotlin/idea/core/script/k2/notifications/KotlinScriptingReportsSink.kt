// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.notifications

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.shared.getScriptReports
import org.jetbrains.kotlin.idea.core.script.shared.scriptDiagnostics
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import kotlin.script.experimental.api.ScriptDiagnostic

class KotlinScriptingReportsSink(
  val project: Project,
) : ScriptReportSink {
    override fun attachReports(scriptFile: VirtualFile, reports: List<ScriptDiagnostic>) {
        if (getScriptReports(scriptFile) == reports) return

        scriptFile.scriptDiagnostics = reports
    }
}