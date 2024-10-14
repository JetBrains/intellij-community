// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.Divider
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Predicates
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.util.CommonProcessors
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.InvalidModuleException
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.RenderingContext
import org.jetbrains.kotlin.diagnostics.rendering.parameters
import org.jetbrains.kotlin.idea.base.analysis.injectionRequiresOnlyEssentialHighlighting
import org.jetbrains.kotlin.idea.base.fe10.highlighting.suspender.KotlinHighlightingSuspender
import org.jetbrains.kotlin.idea.base.highlighting.shouldHighlightErrors
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.statistics.KotlinFailureCollector
import org.jetbrains.kotlin.idea.statistics.compilationError.KotlinCompilationErrorFrequencyStatsCollector
import org.jetbrains.kotlin.idea.util.actionUnderSafeAnalyzeBlock
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.resolve.BindingContext

abstract class AbstractKotlinHighlightVisitor : HighlightVisitor {
    private var afterAnalysisVisitor: Array<AfterAnalysisHighlightingVisitor>? = null
    @Volatile
    private var attempt: Int = 0

    override fun suitableForFile(file: PsiFile): Boolean = file is KtFile

    override fun visit(element: PsiElement) {
        afterAnalysisVisitor?.forEach(element::accept)
    }

    override fun analyze(psiFile: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        val file = psiFile as? KtFile ?: return false
        val project = file.project
        val highlightingLevelManager = HighlightingLevelManager.getInstance(project)
        if (highlightingLevelManager.runEssentialHighlightingOnly(file) || psiFile.injectionRequiresOnlyEssentialHighlighting) {
            return true
        }

        try {
            analyze(file, holder)

            action.run()

            attempt = 0
        } catch (e: Throwable) {
            val unwrappedException = (e as? ProcessCanceledException)?.cause as? InvalidModuleException ?: e
            if (unwrappedException is ControlFlowException) {
                throw e
            }

            KotlinFailureCollector.recordHighlightingFailure(file)

            if (unwrappedException is InvalidModuleException) {
                val currentAttempt = attempt
                if (currentAttempt < ATTEMPT_THRESHOLD) {
                    attempt = currentAttempt + 1
                    throw e
                }
            }

            if (KotlinHighlightingSuspender.getInstance(project).suspend(file.virtualFile)) {
                throw unwrappedException
            } else {
                LOG.warn(unwrappedException)
            }
        } finally {
            afterAnalysisVisitor = null
        }

        return true
    }

    private fun analyze(file: KtFile, holder: HighlightInfoHolder) {
        val dividedElements: List<Divider.DividedElements> = ArrayList()
        Divider.divideInsideAndOutsideAllRoots(
          file, file.textRange, file.textRange, Predicates.alwaysTrue(),
          CommonProcessors.CollectProcessor(dividedElements)
        )
        // TODO: for the sake of check that element belongs to the file
        //  for some reason analyzeWithAllCompilerChecks could return psiElements those do not belong to the file
        //  see [ScriptConfigurationHighlightingTestGenerated$Highlighting.testCustomExtension]
        val elements = dividedElements.flatMap(Divider.DividedElements::inside).toSet()

        // annotate diagnostics on fly: show diagnostics as soon as front-end reports them
        // don't create quick fixes as it could require some resolve
        val highlightInfoByDiagnostic = HashMap<Diagnostic, HighlightInfo>()

        // render of on-fly diagnostics with descriptors could lead to recursion
        fun checkIfDescriptor(candidate: Any?): Boolean =
            candidate is DeclarationDescriptor || candidate is Collection<*> && candidate.any(::checkIfDescriptor)

        val shouldHighlightErrors = file.shouldHighlightErrors()

        val isInjectedCode = isIgnoredInjectedCode(holder)

        val analysisResult =
            if (shouldHighlightErrors) {
                file.analyzeWithAllCompilerChecks(
                    {
                        val element = it.psiElement
                        if (element in elements &&
                            it !in highlightInfoByDiagnostic &&
                            !RenderingContext.parameters(it).any(::checkIfDescriptor)
                        ) {
                            annotateDiagnostic(
                                element, holder, listOf(it), isInjectedCode, highlightInfoByDiagnostic, calculatingInProgress = true
                            )
                        }
                    }
                )
            } else {
                file.analyzeWithAllCompilerChecks()
            }

        // resolve is done!

        val bindingContext =
            file.actionUnderSafeAnalyzeBlock(
                {
                    analysisResult.throwIfError()
                    analysisResult.bindingContext
                },
                { BindingContext.EMPTY }
            )

        afterAnalysisVisitor = getAfterAnalysisVisitor(holder, bindingContext)

        //cleanUpCalculatingAnnotations(highlightInfoByTextRange)
        if (!shouldHighlightErrors) return

        val diagnostics = bindingContext.diagnostics
        diagnostics
            .filter { diagnostic ->
                diagnostic.psiElement in elements
                        // has been processed earlier e.g. on-fly or for some reasons it could be duplicated diagnostics for the same factory
                        //  see [PsiCheckerTestGenerated$Checker.testRedeclaration]
                        && diagnostic !in highlightInfoByDiagnostic
            }
            .groupBy { it.psiElement }
            .forEach { (psiElement, diagnostics) ->
                // annotate diagnostics those were not possible to report (and therefore render) on-the-fly
                annotateDiagnostic(psiElement, holder, diagnostics, isInjectedCode, highlightInfoByDiagnostic, calculatingInProgress = false)
            }

        // apply quick fixes for all diagnostics grouping by element
        highlightInfoByDiagnostic.keys
            .groupBy { it.psiElement }
            .forEach {
                annotateDiagnostic(it.key, holder, it.value, isInjectedCode, highlightInfoByDiagnostic, calculatingInProgress = false)
            }
        KotlinCompilationErrorFrequencyStatsCollector.recordCompilationErrorsHappened(
            diagnostics.asSequence().filter { it.severity == Severity.ERROR }.map(Diagnostic::factoryName),
            file
        )
    }

    private fun annotateDiagnostic(
        element: PsiElement,
        holder: HighlightInfoHolder,
        diagnostics: List<Diagnostic>,
        isInjectedCode: Boolean,
        highlightInfoByDiagnostic: MutableMap<Diagnostic, HighlightInfo>? = null,
        calculatingInProgress: Boolean = true
    ) {
        if (element.getUserData(DO_NOT_HIGHLIGHT_KEY) != null) return
        assertBelongsToTheSameElement(element, diagnostics)
        if (element is KtNameReferenceExpression) {
            val unresolved = diagnostics.any { it.factory == Errors.UNRESOLVED_REFERENCE }
            element.putUserData(UNRESOLVED_KEY, if (unresolved) Unit else null)
        }

        val activeDiagnostics = if (!isInjectedCode) {
            diagnostics
        }
        else {
            diagnostics.filter { it.factoryName !in suppressedInjectedFilesDiagnostics }
        }

        if (activeDiagnostics.isNotEmpty()) {
            ElementAnnotator(element) { shouldSuppressUnusedParameter(it) }
                .registerDiagnosticsAnnotations(holder, activeDiagnostics, highlightInfoByDiagnostic, calculatingInProgress)
        }
    }

    private fun isIgnoredInjectedCode(holder: HighlightInfoHolder): Boolean {
        val file = holder.annotationSession.file
        if (file.virtualFile?.extension == "kts") {
            // exclude scripts and notebook cells
            return false
        }

        return InjectedLanguageManager.getInstance(holder.project).isInjectedFragment(file)
                || file.getUserData(FileContextUtil.INJECTED_IN_ELEMENT) != null
    }

    protected open fun shouldSuppressUnusedParameter(parameter: KtParameter): Boolean = false

    companion object {
        private val LOG = Logger.getInstance(AbstractKotlinHighlightVisitor::class.java)

        private val UNRESOLVED_KEY = Key<Unit>("KotlinHighlightVisitor.UNRESOLVED_KEY")

        private val DO_NOT_HIGHLIGHT_KEY = Key<Unit>("DO_NOT_HIGHLIGHT_KEY")

        private const val ATTEMPT_THRESHOLD = 10

        private val suppressedInjectedFilesDiagnostics: Set<String> = setOf(
            "UNRESOLVED_REFERENCE",
            "NOTHING_TO_OVERRIDE"
        )

        @JvmStatic
        fun KtElement.suppressHighlight() {
            putUserData(DO_NOT_HIGHLIGHT_KEY, Unit)
            forEachDescendantOfType<KtElement> {
                it.putUserData(DO_NOT_HIGHLIGHT_KEY, Unit)
            }
        }

        @JvmStatic
        fun KtElement.unsuppressHighlight() {
            putUserData(DO_NOT_HIGHLIGHT_KEY, null)
            forEachDescendantOfType<KtElement> {
                it.putUserData(DO_NOT_HIGHLIGHT_KEY, null)
            }
        }

        fun getAfterAnalysisVisitor(holder: HighlightInfoHolder, bindingContext: BindingContext) = arrayOf(
            PropertiesHighlightingVisitor(holder, bindingContext),
            FunctionsHighlightingVisitor(holder, bindingContext),
            VariablesHighlightingVisitor(holder, bindingContext),
            TypeKindHighlightingVisitor(holder, bindingContext)
        )

        fun wasUnresolved(element: KtNameReferenceExpression) = element.getUserData(UNRESOLVED_KEY) != null

        internal fun assertBelongsToTheSameElement(element: PsiElement, diagnostics: Collection<Diagnostic>) {
            assert(diagnostics.all { it.psiElement == element })
        }
    }
}

