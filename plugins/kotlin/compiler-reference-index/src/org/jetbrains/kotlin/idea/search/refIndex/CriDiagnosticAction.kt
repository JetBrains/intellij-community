// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
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
            appendLine("=== Kotlin CRI Diagnostics ===")
            appendLine()

            val dirtyModules = KotlinCompilerReferenceIndexService.getInstanceIfEnabled(project)?.dirtyModules() ?: emptySet()

            appendLine("Dirty Modules (${dirtyModules.size}):")
            if (dirtyModules.isEmpty()) {
                appendLine("  (none - all modules are up-to-date)")
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
