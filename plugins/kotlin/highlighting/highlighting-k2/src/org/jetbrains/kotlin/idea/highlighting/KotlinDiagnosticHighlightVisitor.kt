// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightInfoType.HighlightInfoTypeImpl
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionWithOptions
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixUpdater
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.IncompleteModelUtil.isIncompleteModel
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnostic
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.diagnostics.getDefaultMessageWithFactoryName
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.isInjectedFileShouldBeAnalyzed
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixService
import org.jetbrains.kotlin.idea.highlighter.KotlinUnresolvedReferenceKind
import org.jetbrains.kotlin.idea.highlighter.KotlinUnresolvedReferenceKind.UnresolvedDelegateFunction
import org.jetbrains.kotlin.idea.highlighter.clearAllKotlinUnresolvedReferenceKinds
import org.jetbrains.kotlin.idea.highlighter.registerKotlinUnresolvedReferenceKind
import org.jetbrains.kotlin.idea.highlighting.highlighters.ignoreIncompleteModeDiagnostics
import org.jetbrains.kotlin.idea.inspections.suppress.CompilerWarningIntentionAction
import org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressableWarningProblemGroup
import org.jetbrains.kotlin.idea.statistics.compilationError.KotlinCompilationErrorFrequencyStatsCollector
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile


class KotlinDiagnosticHighlightVisitor : HighlightVisitor {
    // map TextRange -> list of diagnostics for that range obtained from collectDiagnosticsForFile()
    // we have to extract diags from this map according to the range of the current element being visited, to avoid flickers
    private lateinit var diagnosticRanges: MutableMap<TextRange, MutableList<HighlightInfo.Builder?>>
    private var holder: HighlightInfoHolder? = null
    override fun suitableForFile(file: PsiFile): Boolean {
        val viewProvider = file.viewProvider
        val isInjection = InjectedLanguageManager.getInstance(file.project).isInjectedViewProvider(viewProvider)
        if (isInjection && !viewProvider.isInjectedFileShouldBeAnalyzed) {
            // do not highlight errors in injected code
            return false
        }
        return file is KtFile
    }

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        val highlightingLevelManager = HighlightingLevelManager.getInstance(file.project)
        if (highlightingLevelManager.runEssentialHighlightingOnly(file)) {
            return true
        }
        this.holder = holder
        val contextFile = holder.contextFile as? KtFile ?: error("KtFile files expected but got ${holder.contextFile}")
        diagnosticRanges = analyzeFile(contextFile)
        try {
            action.run()
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e
            // TODO: Port KotlinHighlightingSuspender to K2 to avoid the issue with infinite highlighting loop restart
            throw e
        } finally {
            // do not leak Editor, since KotlinDiagnosticHighlightVisitor is an app-level extension
            this.holder = null
            diagnosticRanges.clear()
        }
        return true
    }

    private fun analyzeFile(file: KtFile): MutableMap<TextRange, MutableList<HighlightInfo.Builder?>> {
        analyze(file) {

            //remove filtering when KTIJ-29195 is fixed
            val isIJProject = IntelliJProjectUtil.isIntelliJPlatformProject(file.project)
            val analysis = file.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
            val diagnostics = analysis
                .filterOutCodeFragmentVisibilityErrors(file)
                .filterNot { isIJProject && it.diagnosticClass == KaFirDiagnostic.ContextReceiversDeprecated::class }
                .onEach { diagnostic -> diagnostic.psi.clearAllKotlinUnresolvedReferenceKinds() }
                .flatMap { diagnostic -> diagnostic.textRanges.map { range -> Pair(range, diagnostic) } }
                .groupByTo(HashMap(), { it.first }, {
                    try {
                        convertToBuilder(file, it.first, it.second)
                    } catch (e: ProcessCanceledException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.getInstance(KotlinDiagnosticHighlightVisitor::class.java).error(e)
                        null
                    }
                })

            KotlinCompilationErrorFrequencyStatsCollector.recordCompilationErrorsHappened(
                analysis.asSequence().filter { it.severity == KaSeverity.ERROR }.mapNotNull(KaDiagnosticWithPsi<*>::factoryName), file
            )
            return diagnostics
        }
    }

    private fun <PSI : PsiElement> Collection<KaDiagnosticWithPsi<PSI>>.filterOutCodeFragmentVisibilityErrors(file: KtFile): Collection<KaDiagnosticWithPsi<PSI>> {
        if (file !is KtCodeFragment) return this
        return filterNot { diagnostic ->
            diagnostic.diagnosticClass == KaFirDiagnostic.InvisibleReference::class
                    || diagnostic.diagnosticClass == KaFirDiagnostic.InvisibleSetter::class
        }
    }

    context(KaSession)
    private fun convertToBuilder(file: KtFile, range: TextRange, diagnostic: KaDiagnosticWithPsi<*>): HighlightInfo.Builder {
        val isWarning = diagnostic.severity == KaSeverity.WARNING
        val psiElement = diagnostic.psi
        val factoryName = diagnostic.factoryName
        val fixes = KotlinQuickFixService.getInstance().getQuickFixesFor(diagnostic).takeIf { it.isNotEmpty() }
            ?: if (isWarning) listOf(CompilerWarningIntentionAction(factoryName)) else emptyList()
        val problemGroup = if (isWarning) {
            KotlinSuppressableWarningProblemGroup(factoryName)
        } else null

        val infoBuilder = getHighlightInfoBuilder(diagnostic, range)

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

        if (diagnostic is KaFirDiagnostic.DelegateSpecialFunctionMissing) {
            psiElement.registerKotlinUnresolvedReferenceKind(UnresolvedDelegateFunction(diagnostic.expectedFunctionSignature))
        }

        if (diagnostic is KaFirDiagnostic.DelegateSpecialFunctionNoneApplicable) {
            psiElement.registerKotlinUnresolvedReferenceKind(UnresolvedDelegateFunction(diagnostic.expectedFunctionSignature))
        }

        if (
            diagnostic is KaFirDiagnostic.UnresolvedReference ||
            diagnostic is KaFirDiagnostic.UnresolvedReferenceWrongReceiver ||
            diagnostic is KaFirDiagnostic.UnresolvedImport
        ) {
            psiElement.registerKotlinUnresolvedReferenceKind(KotlinUnresolvedReferenceKind.Regular)
        }

        if (
            diagnostic is KaFirDiagnostic.UnresolvedImport ||
            diagnostic is KaFirDiagnostic.UnresolvedReference ||
            diagnostic is KaFirDiagnostic.UnresolvedReferenceWrongReceiver ||
            diagnostic is KaFirDiagnostic.DelegateSpecialFunctionMissing ||
            diagnostic is KaFirDiagnostic.DelegateSpecialFunctionNoneApplicable
        ) {
            psiElement.reference?.let { ref ->
                UnresolvedReferenceQuickFixUpdater.getInstance(file.project).registerQuickFixesLater(ref, infoBuilder)
            }
        }

        return infoBuilder
    }

    context(KaSession)
    private fun getHighlightInfoBuilder(
        diagnostic: KaDiagnosticWithPsi<*>,
        range: TextRange
    ): HighlightInfo.Builder {
        return if (diagnostic.diagnosticClass !in ignoreIncompleteModeDiagnostics
            && isIncompleteModel(diagnostic.psi)
            && diagnostic.severity == KaSeverity.ERROR
        ) {
            val message = K2HighlightingBundle.message("text.required.dependency.not.loaded.yet")
            val htmlMessage = XmlStringUtil.wrapInHtml(message)
            HighlightInfo.newHighlightInfo(INCOMPLETE_MODE_ERROR)
                .escapedToolTip(htmlMessage)
                .description(message)
                .range(range)
        } else {
            val message = diagnostic.getMessageToRender()
            val htmlMessage = XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(message).replace("\n", "<br>"))
            HighlightInfo.newHighlightInfo(diagnostic.getHighlightInfoType())
                .escapedToolTip(htmlMessage)
                .description(message)
                .range(range)
        }
    }

    @NlsSafe
    private fun KaDiagnostic.getMessageToRender(): String =
        if (isInternalOrUnitTestMode())
            getDefaultMessageWithFactoryName()
        else defaultMessage

    private fun isInternalOrUnitTestMode(): Boolean {
        val application = ApplicationManager.getApplication()
        return application.isInternal || application.isUnitTestMode
    }


    context(KaSession)
    private fun KaDiagnosticWithPsi<*>.getHighlightInfoType(): HighlightInfoType {
        return when {
            isUnresolvedDiagnostic() -> HighlightInfoType.WRONG_REF
            isDeprecatedDiagnostic() -> HighlightInfoType.DEPRECATED
            else -> when (severity) {
                KaSeverity.INFO -> HighlightInfoType.INFORMATION
                KaSeverity.ERROR -> HighlightInfoType.ERROR
                KaSeverity.WARNING -> HighlightInfoType.WARNING
            }
        }
    }

    context(KaSession)
    private fun KaDiagnosticWithPsi<*>.isUnresolvedDiagnostic() = when (this) {
        is KaFirDiagnostic.UnresolvedReference -> true
        is KaFirDiagnostic.UnresolvedLabel -> true
        is KaFirDiagnostic.UnresolvedReferenceWrongReceiver -> true
        is KaFirDiagnostic.UnresolvedImport -> true
        is KaFirDiagnostic.MissingStdlibClass -> true
        else -> false
    }

    context(KaSession)
    private fun KaDiagnosticWithPsi<*>.isDeprecatedDiagnostic() = when (this) {
        is KaFirDiagnostic.Deprecation -> true
        else -> false
    }

    override fun visit(element: PsiElement) {
        val elementRange = element.textRange
        // show diagnostics with textRanges under this element range
        // assumption: highlight visitors call visit() method in the post-order (children first)
        // note that after this visitor finished, `diagnosticRanges` will be empty, because all diagnostics are inside the file range, by definition
        val iterator = diagnosticRanges.iterator()
        for (entry in iterator) {
            if (entry.key in elementRange) {
                val diagnostics = entry.value.filterNotNull()
                for (builder in diagnostics) {
                    holder!!.add(builder.create())
                }
                iterator.remove()
            }
        }
    }

    override fun clone(): HighlightVisitor {
        return KotlinDiagnosticHighlightVisitor()
    }
}

val INCOMPLETE_MODE_ERROR = HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.INFORMATION_ATTRIBUTES)
