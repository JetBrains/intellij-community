// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.visitor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionWithOptions
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
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
import org.jetbrains.kotlin.idea.highlighter.operationReferenceForBinaryExpressionOrThis
import org.jetbrains.kotlin.idea.highlighting.K2HighlightingBundle
import org.jetbrains.kotlin.idea.highlighting.analyzers.ignoreIncompleteModeDiagnostics
import org.jetbrains.kotlin.idea.inspections.suppress.CompilerWarningIntentionAction
import org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressableWarningProblemGroup
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.statistics.compilationError.KotlinCompilationErrorFrequencyStatsCollector
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtScript
import kotlin.coroutines.cancellation.CancellationException

internal class KotlinDiagnosticHighlightVisitor : HighlightVisitor, HighlightRangeExtension {
    /**
     * map [PsiElement] -> list of highlighting builders for this element, built in [analyzeFile]
     * This map is required to extract diagnostics exactly when the current element is being visited, to avoid flickers
     */
    private var diagnosticsMap: Map<PsiElement, List<HighlightInfo.Builder>> = emptyMap()
    private var holder: HighlightInfoHolder? = null
    private var coroutineScope: CoroutineScope? = null
    override fun suitableForFile(file: PsiFile): Boolean {
        return shouldHighlightDiagnostics(file)
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
            if (Logger.shouldRethrow(e)) throw e
            // TODO: Port KotlinHighlightingSuspender to K2 to avoid the issue with infinite highlighting loop restart
            throw e
        } finally {
            // do not leak Editor, since KotlinDiagnosticHighlightVisitor is a project-level extension
            this.diagnosticsMap = emptyMap()
            this.coroutineScope?.cancel() // TODO
            this.coroutineScope = null
            this.holder = null
        }

        return true
    }

    @OptIn(KaExperimentalApi::class)
    private fun analyzeFile(file: KtFile): Map<PsiElement, List<HighlightInfo.Builder>> = analyze(file) {
        // Trigger additional resolution under `analyze` block to have the session on the stack
        // to avoid stop-the-world and GC optimizations
        if (Registry.`is`(key = "kotlin.highlighting.warmup", defaultValue = true)) {
            triggerCollectingDiagnostics(file)
        }

        val analysis = file.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
        val filteredAnalysisResult = analysis
            .filterOutCodeFragmentVisibilityErrors(file)

        val builders = filteredAnalysisResult
            .map { diagnostic ->
                Pair(
                    diagnostic.psi,
                     try {
                         diagnostic.textRanges.map { range ->
                             convertToBuilder(file, range, diagnostic)
                         }
                     } catch (e: CancellationException) {
                         throw e
                     } catch (e: Throwable) {
                         Logger.getInstance(KotlinDiagnosticHighlightVisitor::class.java).error(e)
                         emptyList()
                     })
            }

        // psi elements in the list can duplicate, but infrequently
        val destination = HashMap<PsiElement, List<HighlightInfo.Builder>>(builders.size)
        for (pair in builders) {
            destination.compute(pair.first) { _, oldList -> if (oldList == null) pair.second else oldList + pair.second }
        }

        KotlinCompilationErrorFrequencyStatsCollector.recordCompilationErrorsHappened(
            analysis.asSequence().filter { it.severity == KaSeverity.ERROR }.mapNotNull(KaDiagnosticWithPsi<*>::factoryName), file
        )

        destination
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
        val quickFixService = KotlinQuickFixService.getInstance()
        val quickFixesForDiagnostic = with(quickFixService) { getQuickFixesFor(diagnostic) }
        val fixes = quickFixesForDiagnostic.takeIf { it.isNotEmpty() }
            ?: if (isWarning) listOf(CompilerWarningIntentionAction(factoryName)) else emptyList()
        val problemGroup = if (isWarning) {
            KotlinSuppressableWarningProblemGroup(factoryName)
        } else null

        val builder = createHighlightInfo(diagnostic, range)

        if (problemGroup != null) {
            builder.problemGroup(problemGroup)
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
            builder.registerFix(quickFixInfo, options, null, null, null)
        }

        registerLazyFixes(builder, quickFixService, diagnostic)

        if (
            diagnostic is KaFirDiagnostic.UnresolvedImport ||
            diagnostic is KaFirDiagnostic.UnresolvedReference ||
            diagnostic is KaFirDiagnostic.UnresolvedReferenceWrongReceiver ||
            diagnostic is KaFirDiagnostic.InvisibleReference
        ) {
            /*
            Two points here:
            1. It's enough to register only the main reference here, because later on we rely on the underlying PSI element anyway.
            2. If the diagnostic points to a binary expression, we use the operation expression to get the reference (see KT-75331).
            */
            val mainReference = (psiElement.operationReferenceForBinaryExpressionOrThis as? KtElement)?.mainReference

            mainReference?.let { ref ->
                UnresolvedReferenceQuickFixProvider.registerUnresolvedReferenceLazyQuickFixes(ref, builder)
            }
        }

        return builder
    }

    private fun KaSession.registerLazyFixes(
        builder: HighlightInfo.Builder,
        quickFixService: KotlinQuickFixService,
        originalDiagnostic: KaDiagnosticWithPsi<*>,
    ) {
        if (!with(quickFixService) { canProduceLazyQuickFixesFor(originalDiagnostic) }) return

        val diagnosticPsiPointer = originalDiagnostic.psi.createSmartPointer()
        val diagnosticFactoryName = originalDiagnostic.factoryName

        builder.registerLazyFixes { registrar ->
            val restoredPsi = diagnosticPsiPointer.element as? KtElement ?: return@registerLazyFixes

            analyze(restoredPsi) {
                @OptIn(KaExperimentalApi::class)
                val restoredDiagnostics = restoredPsi
                    .diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                    .filter { it.factoryName == diagnosticFactoryName }

                for (diagnostic in restoredDiagnostics) {
                    for (quickFix in with(quickFixService) { getLazyQuickFixesFor(diagnostic) }) {
                        registrar.register(quickFix)
                    }
                }
            }
        }
    }

    private fun createHighlightInfo(
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
        return (application.isInternal || application.isUnitTestMode) &&
                Registry.`is`("kotlin.highlighting.internal.show.diagnostic.names", true)
    }

    private fun getHighlightInfoType(psi: KaDiagnosticWithPsi<*>): HighlightInfoType = when {
        isUnresolvedDiagnostic(psi) -> HighlightInfoType.WRONG_REF
        isDeprecatedDiagnostic(psi) -> HighlightInfoType.DEPRECATED
        isUnusedElementDiagnostic(psi) -> HighlightInfoType.UNUSED_SYMBOL
        else -> when (psi.severity) {
            KaSeverity.INFO -> HighlightInfoType.INFORMATION
            KaSeverity.ERROR -> HighlightInfoType.ERROR
            KaSeverity.WARNING -> HighlightInfoType.WARNING
        }
    }

    private fun isUnresolvedDiagnostic(psi: KaDiagnosticWithPsi<*>) = when (psi) {
        is KaFirDiagnostic.UnresolvedReference -> true
        is KaFirDiagnostic.UnresolvedLabel -> true
        is KaFirDiagnostic.UnresolvedReferenceWrongReceiver -> true
        is KaFirDiagnostic.UnresolvedImport -> true
        is KaFirDiagnostic.MissingStdlibClass -> true
        else -> false
    }

    private fun isDeprecatedDiagnostic(psi: KaDiagnosticWithPsi<*>) = when (psi) {
        is KaFirDiagnostic.Deprecation -> true
        else -> false
    }

    private fun isUnusedElementDiagnostic(psi: KaDiagnosticWithPsi<*>) = when (psi) {
        is KaFirDiagnostic.UselessCast -> true
        is KaFirDiagnostic.UselessElvis -> true
        is KaFirDiagnostic.UselessIsCheck -> true
        else -> false
    }

    override fun visit(element: PsiElement) {
        // show diagnostics under this element range
        // assumption: highlight visitors call visit() method in the post-order (children first)
        // note that after this visitor finished, `diagnosticRanges` will be empty,
        // because all diagnostics are inside the file, by definition
        val diagnostics = diagnosticsMap.get(element) ?: return
        for (builder in diagnostics) {
            val info = builder.create() ?: continue
            holder!!.add(info)
        }
    }


    override fun clone(): HighlightVisitor {
        return KotlinDiagnosticHighlightVisitor()
    }

    companion object {
        fun shouldHighlightDiagnostics(file: PsiFile): Boolean {
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
    }
}
