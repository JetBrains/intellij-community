// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.jetbrains.kotlin.checkers.utils.CheckerTestUtil
import org.jetbrains.kotlin.checkers.utils.DiagnosticsRenderingConfiguration
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.psi.KtFile
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class CopyAsDiagnosticTestAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        assert(editor != null && psiFile != null)

        val bindingContext = (psiFile as KtFile).analyzeWithContent()

        // Parameters `languageVersionSettings`, `dataFlowValueFactory` and `moduleDescriptor` are not-null only for compiler diagnostic tests
        val diagnostics = CheckerTestUtil.getDiagnosticsIncludingSyntaxErrors(
            bindingContext,
            psiFile,
            false,
            mutableListOf(),
            DiagnosticsRenderingConfiguration(null, false, null),
            null,
            null
        )
        val result = CheckerTestUtil.addDiagnosticMarkersToText(psiFile, diagnostics).toString()

        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(result)) { _, _ -> }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = isApplicationInternalMode()
                && e.getData(CommonDataKeys.EDITOR) != null
                && e.getData(CommonDataKeys.PSI_FILE) is KtFile
    }
}
