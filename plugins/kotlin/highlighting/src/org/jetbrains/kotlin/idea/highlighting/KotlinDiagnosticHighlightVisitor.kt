// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionWithOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.util.NlsSafe
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
import org.jetbrains.kotlin.idea.inspections.suppress.CompilerWarningIntentionAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixService
import org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressableWarningProblemGroup
import org.jetbrains.kotlin.idea.statistics.compilationError.KotlinCompilationErrorFrequencyStatsCollector
import org.jetbrains.kotlin.psi.KtFile

class KotlinDiagnosticHighlightVisitor : HighlightVisitor {

    override fun suitableForFile(file: PsiFile): Boolean {
        return file is KtFile
    }

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        if (file !is KtFile) return false
        val highlightingLevelManager = HighlightingLevelManager.getInstance(file.project)
        if (highlightingLevelManager.runEssentialHighlightingOnly(file)) {
            return true
        }
        try {
            analyze(file, holder)
            action.run()
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e
            // TODO: Port KotlinHighlightingSuspender to K2 to avoid the issue with infinite highlighting loop restart
            throw e
        }
        return true
    }

    private fun analyze(file: KtFile, holder: HighlightInfoHolder) {
        analyze(file) {
            val diagnostics = file.collectDiagnosticsForFile(KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
            for (diagnostic in diagnostics) {
                addDiagnostic(diagnostic, holder)
            }
            KotlinCompilationErrorFrequencyStatsCollector.recordCompilationErrorsHappened(
                diagnostics.asSequence().filter { it.severity == Severity.ERROR }.mapNotNull(KtDiagnosticWithPsi<*>::factoryName),
                file
            )
        }
    }

    context(KtAnalysisSession)
    private fun addDiagnostic(diagnostic: KtDiagnosticWithPsi<*>, holder: HighlightInfoHolder) {
        val isWarning = diagnostic.severity == Severity.WARNING
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
                val options = mutableListOf<IntentionAction>()
                if (quickFixInfo is IntentionActionWithOptions) {
                    options += quickFixInfo.options
                }
                if (problemGroup != null) {
                    options += problemGroup.getSuppressActions(diagnostic.psi)
                }
                infoBuilder.registerFix(quickFixInfo, options, null, null, null)
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
        // After-analysis highlighting visitors are implemented as a separate highlighting pass, see [KotlinSemanticHighlightingPass],
        // so this method that is called after the analysis is empty.
    }

    override fun clone(): HighlightVisitor {
        return KotlinDiagnosticHighlightVisitor()
    }
}
