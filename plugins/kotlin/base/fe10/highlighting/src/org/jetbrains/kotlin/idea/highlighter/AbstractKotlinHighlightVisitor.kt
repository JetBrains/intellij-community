// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.Divider
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.CommonProcessors
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.rendering.RenderingContext
import org.jetbrains.kotlin.diagnostics.rendering.parameters
import org.jetbrains.kotlin.idea.base.fe10.highlighting.suspender.KotlinHighlightingSuspender
import org.jetbrains.kotlin.idea.base.highlighting.shouldHighlightErrors
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.util.actionUnderSafeAnalyzeBlock
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.resolve.BindingContext

abstract class AbstractKotlinHighlightVisitor : HighlightVisitor {
    private var afterAnalysisVisitor: Array<AfterAnalysisHighlightingVisitor>? = null

    override fun suitableForFile(file: PsiFile) = file is KtFile

    override fun visit(element: PsiElement) {
        afterAnalysisVisitor?.forEach(element::accept)
    }

    override fun analyze(psiFile: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        val file = psiFile as? KtFile ?: return false
        val highlightingLevelManager = HighlightingLevelManager.getInstance(file.project)
        if (highlightingLevelManager.runEssentialHighlightingOnly(file)) {
            return true
        }

        try {
            analyze(file, holder)

            action.run()
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e

            if (KotlinHighlightingSuspender.getInstance(file.project).suspend(file.virtualFile)) {
                throw e
            } else {
                LOG.warn(e)
            }
        } finally {
            afterAnalysisVisitor = null
        }

        return true
    }

    private fun analyze(file: KtFile, holder: HighlightInfoHolder) {
        val dividedElements: List<Divider.DividedElements> = ArrayList()
        Divider.divideInsideAndOutsideAllRoots(
            file, file.textRange, file.textRange, { true },
            CommonProcessors.CollectProcessor(dividedElements)
        )
        // TODO: for the sake of check that element belongs to the file
        //  for some reason analyzeWithAllCompilerChecks could return psiElements those do not belong to the file
        //  see [ScriptConfigurationHighlightingTestGenerated$Highlighting.testCustomExtension]
        val elements = dividedElements.flatMap(Divider.DividedElements::inside).toSet()

        // annotate diagnostics on fly: show diagnostics as soon as front-end reports them
        // don't create quick fixes as it could require some resolve
        val diagnosticHighlighted = mutableSetOf<Diagnostic>()

        // render of on-fly diagnostics with descriptors could lead to recursion
        fun checkIfDescriptor(candidate: Any?): Boolean =
            candidate is DeclarationDescriptor || candidate is Collection<*> && candidate.any(::checkIfDescriptor)

        val shouldHighlightErrors = file.shouldHighlightErrors()

        val analysisResult =
            if (shouldHighlightErrors) {
                file.analyzeWithAllCompilerChecks(
                    {
                        val element = it.psiElement
                        if (element in elements &&
                            it !in diagnosticHighlighted &&
                            !RenderingContext.parameters(it).any(::checkIfDescriptor)
                        ) {
                            annotateDiagnostic(element, holder, it, diagnosticHighlighted)
                        }
                    }
                )
            }
            else {
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

        if (!shouldHighlightErrors) return

        for (diagnostic in bindingContext.diagnostics) {
            val psiElement = diagnostic.psiElement
            if (psiElement !in elements) continue
            // has been processed earlier e.g. on-fly or for some reasons it could be duplicated diagnostics for the same factory
            //  see [PsiCheckerTestGenerated$Checker.testRedeclaration]
            if (diagnostic in diagnosticHighlighted) continue

            // annotate diagnostics those were not possible to report (and therefore render) on-the-fly
            annotateDiagnostic(psiElement, holder, diagnostic, diagnosticHighlighted)
        }
    }

    private fun annotateDiagnostic(
        element: PsiElement,
        holder: HighlightInfoHolder,
        diagnostic: Diagnostic,
        diagnosticHighlighted: MutableSet<Diagnostic>
    ) {
        if (element.getUserData(DO_NOT_HIGHLIGHT_KEY) != null) return
        val diagnostics = listOf(diagnostic)
        assertBelongsToTheSameElement(element, diagnostics)
        if (element is KtNameReferenceExpression) {
            val unresolved = diagnostics.any { it.factory == Errors.UNRESOLVED_REFERENCE }
            element.putUserData(UNRESOLVED_KEY, if (unresolved) Unit else null)
        }
        ElementAnnotator(element) { shouldSuppressUnusedParameter(it) }
            .registerDiagnosticsAnnotations(holder, diagnostics, diagnosticHighlighted)
    }

    protected open fun shouldSuppressUnusedParameter(parameter: KtParameter): Boolean = false

    companion object {
        private val LOG = Logger.getInstance(AbstractKotlinHighlightVisitor::class.java)

        private val UNRESOLVED_KEY = Key<Unit>("KotlinHighlightVisitor.UNRESOLVED_KEY")

        private val DO_NOT_HIGHLIGHT_KEY = Key<Unit>("DO_NOT_HIGHLIGHT_KEY")

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

