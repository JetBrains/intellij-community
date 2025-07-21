// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.shared.getScriptReports
import org.jetbrains.kotlin.idea.core.script.shared.scriptDiagnostics
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import kotlin.script.experimental.api.ScriptDiagnostic

class IdeScriptReportSink(
  val project: Project,
  private val coroutineScope: CoroutineScope
) : ScriptReportSink {
    override fun attachReports(scriptFile: VirtualFile, reports: List<ScriptDiagnostic>) {
        if (getScriptReports(scriptFile) == reports) return

        // TODO: persist errors between launches?
        scriptFile.scriptDiagnostics = reports

        coroutineScope.launch {
          readAction {
            PsiManager.getInstance(project).findFile(scriptFile)?.let {
              DaemonCodeAnalyzer.getInstance(project).restart(it)
            }

            EditorNotifications.getInstance(project).updateAllNotifications()
          }
        }
    }
}