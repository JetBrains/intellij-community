// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.UserDataProperty
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

class KotlinScriptingReportsSink(
    val project: Project,
) : ScriptReportSink {
    override fun attachReports(scriptFile: VirtualFile, reports: List<ScriptDiagnostic>) {
        if (getScriptReports(scriptFile) == reports) return

        scriptFile.scriptDiagnostics = reports
    }
}

fun getScriptReports(file: VirtualFile): List<ScriptDiagnostic> {
    return file.scriptDiagnostics ?: emptyList()
}

fun getScriptReports(file: KtFile): List<ScriptDiagnostic> {
    return file.originalFile.virtualFile?.scriptDiagnostics ?: emptyList()
}

fun drainScriptReports(file: KtFile): List<ScriptDiagnostic> {
    val virtualFile = file.originalFile.virtualFile
    val diagnostics = virtualFile?.scriptDiagnostics  ?: emptyList()
    virtualFile.scriptDiagnostics = emptyList()

    return diagnostics
}

private var VirtualFile.scriptDiagnostics: List<ScriptDiagnostic>? by UserDataProperty(Key.create("KOTLIN_SCRIPT_DIAGNOSTICS"))