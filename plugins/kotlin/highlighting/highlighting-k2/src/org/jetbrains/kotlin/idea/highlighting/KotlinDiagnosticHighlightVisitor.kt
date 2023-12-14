// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionWithOptions
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixUpdater
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.diagnostics.getDefaultMessageWithFactoryName
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixService
import org.jetbrains.kotlin.idea.inspections.suppress.CompilerWarningIntentionAction
import org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressableWarningProblemGroup
import org.jetbrains.kotlin.idea.statistics.compilationError.KotlinCompilationErrorFrequencyStatsCollector
import org.jetbrains.kotlin.psi.KtFile

class KotlinDiagnosticHighlightVisitor : HighlightVisitor {
    private lateinit var diagnosticRanges: MutableMap<TextRange, List<KtDiagnosticWithPsi<*>>>
    private var holder: HighlightInfoHolder? = null
    override fun suitableForFile(file: PsiFile): Boolean {
        return file is KtFile
    }

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        val highlightingLevelManager = HighlightingLevelManager.getInstance(file.project)
        if (highlightingLevelManager.runEssentialHighlightingOnly(file)) {
            return true
        }
        this.holder = holder
        diagnosticRanges = compileFile(file as KtFile)
        try {
            action.run()
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e
            // TODO: Port KotlinHighlightingSuspender to K2 to avoid the issue with infinite highlighting loop restart
            throw e
        } finally {
            // do not leak Editor, since KotlinDiagnosticHighlightVisitor is an app-level extension
            this.holder = null
        }
        return true
    }

    private fun compileFile(file: KtFile): MutableMap<TextRange, List<KtDiagnosticWithPsi<*>>> {
        analyze(file) {
            val diagnostics = file.collectDiagnosticsForFile(KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                .flatMap { diagnostic -> diagnostic.textRanges.map { range -> Pair(range, diagnostic) } }
                .groupBy({ it.first }, { it.second })
            KotlinCompilationErrorFrequencyStatsCollector.recordCompilationErrorsHappened(
                diagnostics.values.asSequence().flatten().filter { it.severity == Severity.ERROR }.mapNotNull(KtDiagnosticWithPsi<*>::factoryName),
                file
            )
            return HashMap(diagnostics)
        }
    }

    context(KtAnalysisSession)
    private fun addDiagnostic(file: KtFile, diagnostic: KtDiagnosticWithPsi<*>, holder: HighlightInfoHolder) {
        val isWarning = diagnostic.severity == Severity.WARNING
        val psiElement = diagnostic.psi
        val factoryName = diagnostic.factoryName
        val fixes = KotlinQuickFixService.getInstance().getQuickFixesFor(diagnostic).takeIf { it.isNotEmpty() }
            ?: if (isWarning && factoryName != null) listOf(CompilerWarningIntentionAction(factoryName)) else emptyList()
        val problemGroup = if (isWarning && factoryName != null) {
            KotlinSuppressableWarningProblemGroup(factoryName)
        } else null
        diagnostic.textRanges.forEach { range ->
            val infoBuilder = HighlightInfo.newHighlightInfo(diagnostic.getHighlightInfoType())
                .descriptionAndTooltip(diagnostic.getMessageToRender())
                .range(range)
            if (problemGroup != null) {
                infoBuilder.problemGroup(problemGroup)
            }
            for (quickFixInfo in fixes) {
                // to trigger modCommand.getPresentation() to get `Fix all` and other options
                if (quickFixInfo.asModCommandAction() != null && !quickFixInfo.isAvailable(file.project, null, file)) continue

                val options = mutableListOf<IntentionAction>()
                if (quickFixInfo is IntentionActionWithOptions) {
                    options += quickFixInfo.options
                }
                if (problemGroup != null) {
                    options += problemGroup.getSuppressActions(psiElement)
                }
                infoBuilder.registerFix(quickFixInfo, options, null, null, null)
            }
            if (diagnostic is KtFirDiagnostic.UnresolvedImport || diagnostic is KtFirDiagnostic.UnresolvedReference) {
                psiElement.reference?.let {
                    UnresolvedReferenceQuickFixUpdater.getInstance(holder.project).registerQuickFixesLater(it, infoBuilder)
                }
            }
            holder.add(infoBuilder.create())
        }
    }

    @NlsSafe
    private fun KtDiagnostic.getMessageToRender(): String =
        if (isInternalOrUnitTestMode())
            getDefaultMessageWithFactoryName()
        else defaultMessage

    private fun isInternalOrUnitTestMode(): Boolean {
        val application = ApplicationManager.getApplication()
        return application.isInternal || application.isUnitTestMode
    }


    context(KtAnalysisSession)
    private fun KtDiagnosticWithPsi<*>.getHighlightInfoType(): HighlightInfoType {
       return when {
            isUnresolvedDiagnostic() -> HighlightInfoType.WRONG_REF
            isDeprecatedDiagnostic() -> HighlightInfoType.DEPRECATED
            else ->  when (severity) {
                Severity.INFO -> HighlightInfoType.INFORMATION
                Severity.ERROR -> HighlightInfoType.ERROR
                Severity.WARNING -> HighlightInfoType.WARNING
            }
        }
    }

    context(KtAnalysisSession)
    private fun KtDiagnosticWithPsi<*>.isUnresolvedDiagnostic() = when (this) {
        is KtFirDiagnostic.UnresolvedReference -> true
        is KtFirDiagnostic.UnresolvedLabel -> true
        is KtFirDiagnostic.UnresolvedReferenceWrongReceiver -> true
        is KtFirDiagnostic.UnresolvedImport -> true
        is KtFirDiagnostic.MissingStdlibClass -> true
        else -> false
    }

    context(KtAnalysisSession)
    private fun KtDiagnosticWithPsi<*>.isDeprecatedDiagnostic() = when (this) {
        is KtFirDiagnostic.Deprecation -> true
        else -> false
    }

    override fun visit(element: PsiElement) {
        val elementRange = element.textRange
        // show diagnostics with textRanges under this element range
        // assumption: highlight visitors call visit() method in the post-order (children first)
        // note that after this visitor finished, `diagnosticRanges` will be empty, because all diagnostics are inside the file range, by definition
        val iterator = diagnosticRanges.iterator()
        for (entry in iterator) {
            if (elementRange.contains(entry.key)) {
                val diagnostics = entry.value
                for (diagnostic in diagnostics) {
                    analyze (holder!!.contextFile as KtFile) {
                        addDiagnostic(holder!!.contextFile as KtFile, diagnostic, holder!!)
                    }
                }
                iterator.remove()
            }
        }
    }

    override fun clone(): HighlightVisitor {
        return KotlinDiagnosticHighlightVisitor()
    }
}
