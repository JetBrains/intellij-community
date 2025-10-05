// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k2.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.annotation.HighlightSeverity.*
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.core.script.shared.scriptDiagnostics
import org.jetbrains.kotlin.idea.core.script.shared.ScriptDiagnosticFixProvider
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.psi.KtFile
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode

class KotlinScriptHighlightingVisitor : HighlightVisitor {
    private var diagnosticRanges: MutableMap<TextRange, MutableList<HighlightInfo.Builder>>? = null
    private var holder: HighlightInfoHolder? = null

    override fun suitableForFile(file: PsiFile): Boolean {
        return file is KtFile && file.isScript()
    }

    override fun visit(element: PsiElement) {
        val elementRange = element.textRange
        // show diagnostics with textRanges under this element range
        // assumption: highlight visitors call visit() method in the post-order (children first)
        // note that after this visitor finished, `diagnosticRanges` will be empty, because all diagnostics are inside the file range, by definition
        val iterator = diagnosticRanges?.iterator() ?: return
        for (entry in iterator) {
            if (entry.key in elementRange) {
                val diagnostics = entry.value
                for (builder in diagnostics) {
                    holder!!.add(builder.create())
                }
                iterator.remove()
            }
        }
    }

    override fun analyze(
        file: PsiFile,
        updateWholeFile: Boolean,
        holder: HighlightInfoHolder,
        action: Runnable
    ): Boolean {
        this.holder = holder
        try {
            val ktFile = file as KtFile
            val reports = drainScriptReports(file)

            diagnosticRanges = reports.mapNotNull { scriptDiagnostic ->
                val (startOffset, endOffset) = scriptDiagnostic.location?.let<SourceCode.Location, Pair<Int, Int>> {
                    computeOffsets(
                        ktFile.fileDocument,
                        it
                    )
                } ?: (0 to 0)
                val exception = scriptDiagnostic.exception
                val exceptionMessage = if (exception != null) " ($exception)" else ""

                @Suppress("HardCodedStringLiteral")
                val message = scriptDiagnostic.message + exceptionMessage
                val severity = scriptDiagnostic.severity.convertSeverity() ?: return@mapNotNull null
                val annotation = HighlightInfo.newHighlightInfo(HighlightInfo.convertSeverity(severity))
                    .range(startOffset, endOffset)
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

                TextRange(startOffset, endOffset) to mutableListOf(annotation)
            }.toMap().toMutableMap()

            action.run()
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e
            // TODO: Port KotlinHighlightingSuspender to K2 to avoid the issue with infinite highlighting loop restart
            throw e
        } finally {
            // do not leak Editor, since KotlinDiagnosticHighlightVisitor is an app-level extension
            diagnosticRanges = null
            this.holder = null
        }

        return true
    }

    override fun clone(): HighlightVisitor = KotlinScriptHighlightingVisitor()

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

    private fun drainScriptReports(file: KtFile): List<ScriptDiagnostic> {
        val virtualFile = file.originalFile.virtualFile
        val diagnostics = virtualFile?.scriptDiagnostics  ?: emptyList()
        virtualFile.scriptDiagnostics = emptyList()

        return diagnostics
    }
}
