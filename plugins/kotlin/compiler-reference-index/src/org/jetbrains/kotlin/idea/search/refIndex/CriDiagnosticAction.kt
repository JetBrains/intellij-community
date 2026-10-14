// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent


class CriDiagnosticAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val diagnosticInfo = buildString {
            appendLine(KotlinReferenceIndexBundle.message("diagnostics.header"))
            appendLine()
            val moduleCount = ModuleManager.getInstance(project).modules.size
            appendLine(KotlinReferenceIndexBundle.message("diagnostics.modules.total", moduleCount))
            appendLine(KotlinReferenceIndexBundle.message("diagnostics.indexing", DumbService.isDumb(project)))

            val kotlinCompilerReferenceIndexService = KotlinCompilerReferenceIndexService.getInstanceIfEnabled(project) ?: run {
                appendLine(KotlinReferenceIndexBundle.message("diagnostics.disabled"))
                return@buildString
            }

            appendLine(KotlinReferenceIndexBundle.message("diagnostics.build.active", kotlinCompilerReferenceIndexService.isBuildActive()))
            val dirtyModules = kotlinCompilerReferenceIndexService.dirtyModules() ?: run {
                appendLine(KotlinReferenceIndexBundle.message("diagnostics.uninitialized"))
                return@buildString
            }

            val dirtyModuleCount = dirtyModules.size
            val cleanModuleCount = moduleCount - dirtyModuleCount
            val dirtyPercentage = if (moduleCount == 0) 0.0 else dirtyModuleCount * 100.0 / moduleCount
            val cleanPercentage = if (moduleCount == 0) 0.0 else cleanModuleCount * 100.0 / moduleCount
            appendLine(KotlinReferenceIndexBundle.message("diagnostics.modules.dirty", dirtyModuleCount, dirtyPercentage))
            appendLine(KotlinReferenceIndexBundle.message("diagnostics.modules.clean", cleanModuleCount, cleanPercentage))
            appendLine()

            appendLine(KotlinReferenceIndexBundle.message("diagnostics.modules.dirty.list"))
            if (dirtyModules.isEmpty()) {
                appendLine(KotlinReferenceIndexBundle.message("diagnostics.modules.dirty.none"))
            } else {
                dirtyModules.sortedBy { it.name }.forEach { moduleName ->
                    appendLine("  - $moduleName")
                }
            }
            appendLine()
        }

        CriDiagnosticDialog(diagnosticInfo).show()
    }

    private class CriDiagnosticDialog(private val diagnosticInfo: String) : DialogWrapper(null, false) {
        init {
            title = KotlinReferenceIndexBundle.message("dialog.title.kotlin.cri.diagnostics")
            init()
        }

        override fun createCenterPanel(): JComponent {
            val textArea = JBTextArea(diagnosticInfo).apply {
                isEditable = false
                font = Font.decode(Font.MONOSPACED)
            }
            return JBScrollPane(textArea).apply {
                preferredSize = Dimension(700, 500)
            }
        }
    }
}
