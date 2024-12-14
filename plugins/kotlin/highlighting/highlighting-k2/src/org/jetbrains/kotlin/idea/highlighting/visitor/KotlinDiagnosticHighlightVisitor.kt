// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.visitor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionWithOptions
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixUpdater
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.createSmartPointer
import com.intellij.psi.impl.IncompleteModelUtil.isIncompleteModel
import com.intellij.xml.util.XmlStringUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnostic
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.diagnostics.getDefaultMessageWithFactoryName
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.injectionRequiresOnlyEssentialHighlighting
import org.jetbrains.kotlin.idea.base.analysis.isInjectedFileShouldBeAnalyzed
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixService
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.highlighter.KotlinUnresolvedReferenceKind
import org.jetbrains.kotlin.idea.highlighter.KotlinUnresolvedReferenceKind.UnresolvedDelegateFunction
import org.jetbrains.kotlin.idea.highlighter.clearAllKotlinUnresolvedReferenceKinds
import org.jetbrains.kotlin.idea.highlighter.registerKotlinUnresolvedReferenceKind
import org.jetbrains.kotlin.idea.highlighting.K2HighlightingBundle
import org.jetbrains.kotlin.idea.highlighting.analyzers.ignoreIncompleteModeDiagnostics
import org.jetbrains.kotlin.idea.inspections.suppress.CompilerWarningIntentionAction
import org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressableWarningProblemGroup
import org.jetbrains.kotlin.idea.statistics.compilationError.KotlinCompilationErrorFrequencyStatsCollector
import org.jetbrains.kotlin.psi.*


class KotlinDiagnosticHighlightVisitor : HighlightVisitor, HighlightRangeExtension {
    /**
     * A map from particular [PsiElement] from a file to a list of highlighting builders which belongs to it.
     *
     * This map is required to extract diagnostics from this map according to the current element being visited,
     * to avoid flickers
     *
     * @see DiagnosticInfo
     * @see analyzeFile
     */
    private var diagnosticsMap: MutableMap<PsiElement, MutableList<HighlightInfo.Builder?>>? = null
    private var holder: HighlightInfoHolder? = null
    private var coroutineScope: CoroutineScope? = null
    override fun suitableForFile(file: PsiFile): Boolean {
        if (file !is KtFile || file.isCompiled) return false

        val viewProvider = file.viewProvider
        val isInjection = InjectedLanguageManager.getInstance(file.project).isInjectedViewProvider(viewProvider)
        if (isInjection && (!viewProvider.isInjectedFileShouldBeAnalyzed || file.injectionRequiresOnlyEssentialHighlighting)) {
            // do not highlight errors in injected code
            return false
        }

        val highlightingManager = HighlightingLevelManager.getInstance(file.project)
        return highlightingManager.shouldHighlight(file) && !highlightingManager.runEssentialHighlightingOnly(file)
    }

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        this.holder = holder
        this.coroutineScope = KotlinPluginDisposable.getInstance(file.project)
            .coroutineScope
            .childScope(name = "${KotlinDiagnosticHighlightVisitor::class.simpleName}: ${file.name}")

        try {
            val contextFile = holder.contextFile as? KtFile
                ?: error("${KtFile::class.simpleName} files expected but got ${holder.contextFile::class.simpleName}")

            diagnosticsMap = analyzeFile(contextFile)
            action.run()
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e
            // TODO: Port KotlinHighlightingSuspender to K2 to avoid the issue with infinite highlighting loop restart
            throw e
        } finally {
            // do not leak Editor, since KotlinDiagnosticHighlightVisitor is a project-level extension
            this.diagnosticsMap = null
            this.coroutineScope?.cancel() // TODO
            this.coroutineScope = null
            this.holder = null
        }

        return true
    }

    @OptIn(KaExperimentalApi::class)
    private fun analyzeFile(file: KtFile): MutableMap<PsiElement, MutableList<HighlightInfo.Builder?>> = analyze(file) {
        // Trigger additional resolution under `analyze` block to have the session on the stack
        // to avoid stop-the-world and GC optimizations
        if (Registry.`is`(key = "kotlin.highlighting.warmup", defaultValue = true)) {
            triggerCollectingDiagnostics(file)
        }

        //remove filtering when KTIJ-29195 is fixed
        val isIJProject = IntelliJProjectUtil.isIntelliJPlatformProject(file.project)
        val analysis = file.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
        val diagnostics = analysis
            .filterOutCodeFragmentVisibilityErrors(file)
            .filterNot { isIJProject && it.diagnosticClass == KaFirDiagnostic.ContextReceiversDeprecated::class }
            .onEach { diagnostic -> diagnostic.psi.clearAllKotlinUnresolvedReferenceKinds() }
            .flatMap { diagnostic ->
                val anchorElement = diagnostic.psi
                diagnostic.textRanges.map { range -> DiagnosticInfo(anchorElement, range, diagnostic) }
            }
            .groupByTo(HashMap(), { it.anchorElement }, {
                try {
                    convertToBuilder(file, it.diagnosticRange, it.diagnostic)
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

        diagnostics
    }


    /**
     * This is required to not miss reported elements from [diagnosticsMap] during [visit].
     *
     * Example:
     * ```kotlin
     * class MyClass {
     *   fun member() {}
     * }
     * ```
     * Let's imagine [KtClass] `MyClass` has a diagnostic.
     * If [KtNamedFunction] `member` has a diagnostic as well, without [isForceHighlightParents]
     * the class diagnostic won't be reported as whole first-child hierarchy above `member` function
     * ([KtClassBody], [KtClass]) will be marked as broken.
     */
    override fun isForceHighlightParents(file: PsiFile): Boolean = file is KtFile

    /**
     * **Crucial note**: [anchorElement] is some [PsiElement] from a file.
     * This is a contract based on an assumption:
     * [visit] will iterate through all elements, so there will be an [anchorElement].
     * [diagnosticRange] cannot be used for the anchor as it may represent any range that
     * is not required to match with any particular element.
     *
     * @see visit
     * @see diagnosticRange
     */
    private class DiagnosticInfo(
        /**
         * Represents [KaDiagnosticWithPsi.psi] of [diagnostic]
         */
        val anchorElement: PsiElement,

        /**
         * Represents one range of [KaDiagnosticWithPsi.textRanges] from [diagnostic]
         */
        val diagnosticRange: TextRange,
        val diagnostic: KaDiagnosticWithPsi<PsiElement>,
    )

    /**
     * This is a hack to force the Analysis API to calculate and cache the result of diagnostic collectors.
     *
     * [org.jetbrains.kotlin.analysis.api.components.KaDiagnosticProvider.diagnostics] will resolve the corresponding
     * non-local declaration and calculate diagnostics.
     *
     * The following [org.jetbrains.kotlin.analysis.api.components.KaDiagnosticProvider.collectDiagnostics]
     * may see already cached results.
     *
     * In the ideal scenario, most of the declarations should be resolved via [triggerCollectingDiagnostics] on other threads
     * while the initial thread just get information from caches.
     */
    private fun triggerCollectingDiagnostics(element: KtElement) {
        val pointer = element.createSmartPointer()
        coroutineScope!!.launch {
            // This logic is not inside a separate function to simplify CPU snapshot investigations
            readAction {
                val declaration = pointer.element ?: return@readAction
                analyze(declaration) {
                    @OptIn(KaExperimentalApi::class)
                    declaration.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                }
            }
        }

        val declarations = when (element) {
            is KtFile -> element.declarations
            is KtClassOrObject -> element.declarations
            is KtScript -> element.declarations
            else -> null
        }

        declarations?.forEach(::triggerCollectingDiagnostics)
    }

    private fun <PSI : PsiElement> Collection<KaDiagnosticWithPsi<PSI>>.filterOutCodeFragmentVisibilityErrors(file: KtFile): Collection<KaDiagnosticWithPsi<PSI>> {
        if (file !is KtCodeFragment) return this
        return filterNot { diagnostic ->
            diagnostic.diagnosticClass == KaFirDiagnostic.InvisibleReference::class
                    || diagnostic.diagnosticClass == KaFirDiagnostic.InvisibleSetter::class
        }
    }

    private fun KaSession.convertToBuilder(file: KtFile, range: TextRange, diagnostic: KaDiagnosticWithPsi<*>): HighlightInfo.Builder {
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

    private fun KaSession.getHighlightInfoBuilder(
        diagnostic: KaDiagnosticWithPsi<*>,
        range: TextRange
    ): HighlightInfo.Builder {
        return if (diagnostic.diagnosticClass !in ignoreIncompleteModeDiagnostics
            && isIncompleteModel(diagnostic.psi)
            && diagnostic.severity == KaSeverity.ERROR
        ) {
            val message = K2HighlightingBundle.message("incomplete.project.state.pending.reference")

            HighlightInfo.newHighlightInfo(HighlightInfoType.PENDING_REFERENCE)
                .descriptionAndTooltip(message)
                .range(range)
        } else {
            val message = diagnostic.getMessageToRender()
            val htmlMessage = XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(message).replace("\n", "<br>"))
            HighlightInfo.newHighlightInfo(getHighlightInfoType(diagnostic))
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


    private fun KaSession.getHighlightInfoType(psi: KaDiagnosticWithPsi<*>): HighlightInfoType = when {
        isUnresolvedDiagnostic(psi) -> HighlightInfoType.WRONG_REF
        isDeprecatedDiagnostic(psi) -> HighlightInfoType.DEPRECATED
        else -> when (psi.severity) {
            KaSeverity.INFO -> HighlightInfoType.INFORMATION
            KaSeverity.ERROR -> HighlightInfoType.ERROR
            KaSeverity.WARNING -> HighlightInfoType.WARNING
        }
    }

    private fun KaSession.isUnresolvedDiagnostic(psi: KaDiagnosticWithPsi<*>) = when (psi) {
        is KaFirDiagnostic.UnresolvedReference -> true
        is KaFirDiagnostic.UnresolvedLabel -> true
        is KaFirDiagnostic.UnresolvedReferenceWrongReceiver -> true
        is KaFirDiagnostic.UnresolvedImport -> true
        is KaFirDiagnostic.MissingStdlibClass -> true
        else -> false
    }

    private fun KaSession.isDeprecatedDiagnostic(psi: KaDiagnosticWithPsi<*>) = when (psi) {
        is KaFirDiagnostic.Deprecation -> true
        else -> false
    }

    /**
     * @see DiagnosticInfo
     */
    override fun visit(element: PsiElement) {
        // show diagnostics under this element range
        // assumption: highlight visitors call visit() method in the post-order (children first)
        // note that after this visitor finished, `diagnosticRanges` will be empty,
        // because all diagnostics are inside the file, by definition
        val diagnostics = diagnosticsMap?.remove(element) ?: return
        for (builder in diagnostics) {
            val info = builder?.create() ?: continue
            holder!!.add(info)
        }
    }


    override fun clone(): HighlightVisitor {
        return KotlinDiagnosticHighlightVisitor()
    }
}
