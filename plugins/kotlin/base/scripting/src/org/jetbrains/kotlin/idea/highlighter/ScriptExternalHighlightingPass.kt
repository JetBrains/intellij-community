// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeHighlighting.*
import com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.annotation.HighlightSeverity.*
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.core.script.getScriptReports
import org.jetbrains.kotlin.idea.script.ScriptDiagnosticFixProvider
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.psi.KtFile
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode

class ScriptExternalHighlightingPass(
    private val file: KtFile,
    document: Document
) : TextEditorHighlightingPass(file.project, document), DumbAware {
    override fun doCollectInformation(progress: ProgressIndicator) {
        val document = document

        if (!file.isScript()) return
        val reports = getScriptReports(file)

        val infos = reports.mapNotNull { scriptDiagnostic ->
            val (startOffset, endOffset) = scriptDiagnostic.location?.let { computeOffsets(document, it) } ?: (0 to 0)
            val exception = scriptDiagnostic.exception
            val exceptionMessage = if (exception != null) " ($exception)" else ""
            @Suppress("HardCodedStringLiteral")
            val message = scriptDiagnostic.message + exceptionMessage
            val severity = scriptDiagnostic.severity.convertSeverity() ?: return@mapNotNull null
            val annotation = HighlightInfo.newHighlightInfo(HighlightInfo.convertSeverity(severity))
                .range(startOffset,endOffset)
                .descriptionAndTooltip(message)
            if (startOffset == endOffset) {
                // if range is empty, show notification panel in editor
                annotation.fileLevelAnnotation()
            }

            for (provider in ScriptDiagnosticFixProvider.EP_NAME.extensionList) {
                provider.provideFixes(scriptDiagnostic).forEach {
                    annotation.registerFix(it, null, null, null, null)
                }
            }

            annotation.create()
        }

        BackgroundUpdateHighlightersUtil.setHighlightersToEditor(myProject, file, myDocument, 0, file.textLength, infos, id)
    }

    override fun doApplyInformationToEditor() {

    }

    private fun computeOffsets(document: Document, position: SourceCode.Location): Pair<Int, Int> {
        val startOffset = position.start.absolutePos
            ?: run {
                val startLine = position.start.line.coerceLineIn(document)
                document.offsetBy(startLine, position.start.col)
            }

        val endOffset = position.end?.absolutePos
            ?: run {
                val startLine = position.start.line.coerceLineIn(document)
                val endLine = position.end?.line?.coerceAtLeast(startLine)?.coerceLineIn(document) ?: startLine
                document.offsetBy(
                    endLine,
                    position.end?.col ?: document.getLineEndOffset(endLine)
                ).coerceAtLeast(startOffset)
            }

        return startOffset to endOffset
    }

    private fun Int.coerceLineIn(document: Document) = coerceIn(0, document.lineCount - 1)

    private fun Document.offsetBy(line: Int, col: Int): Int =
        (getLineStartOffset(line) + col).coerceIn(getLineStartOffset(line), getLineEndOffset(line))

    private fun ScriptDiagnostic.Severity.convertSeverity(): HighlightSeverity? = when (this) {
        ScriptDiagnostic.Severity.FATAL -> ERROR
        ScriptDiagnostic.Severity.ERROR -> ERROR
        ScriptDiagnostic.Severity.WARNING -> WARNING
        ScriptDiagnostic.Severity.INFO -> INFORMATION
        ScriptDiagnostic.Severity.DEBUG -> if (isApplicationInternalMode()) INFORMATION else null
    }


    class Registrar : TextEditorHighlightingPassFactoryRegistrar {
        override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
            registrar.registerTextEditorHighlightingPass(
                Factory(),
                TextEditorHighlightingPassRegistrar.Anchor.BEFORE,
                Pass.UPDATE_FOLDING,
                false,
                false
            )
        }
    }

    class Factory : TextEditorHighlightingPassFactory {
        override fun createHighlightingPass(psiFile: PsiFile, editor: Editor): TextEditorHighlightingPass? {
            if (psiFile !is KtFile) return null
            return ScriptExternalHighlightingPass(psiFile, editor.document)
        }
    }
}
