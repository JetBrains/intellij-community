// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.Divider
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.CommonProcessors
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.rendering.RenderingContext
import org.jetbrains.kotlin.diagnostics.rendering.parameters
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.idea.util.actionUnderSafeAnalyzeBlock
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.KotlinType
import java.lang.reflect.*
import java.util.*

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
        val highlightInfoByDiagnostic = mutableMapOf<Diagnostic, HighlightInfo>()
        val highlightInfoByTextRange = mutableMapOf<TextRange, HighlightInfo>()

        // render of on-fly diagnostics with descriptors could lead to recursion
        fun checkIfDescriptor(candidate: Any?): Boolean =
            candidate is DeclarationDescriptor || candidate is Collection<*> && candidate.any(::checkIfDescriptor)

        val shouldHighlightErrors = KotlinHighlightingUtil.shouldHighlightErrors(file)

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
                                element,
                                holder,
                                it,
                                highlightInfoByDiagnostic,
                                highlightInfoByTextRange
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

        cleanUpCalculatingAnnotations(highlightInfoByTextRange)
        if (!shouldHighlightErrors) return

        for (diagnostic in bindingContext.diagnostics) {
            val psiElement = diagnostic.psiElement
            if (psiElement !in elements) continue
            // has been processed earlier e.g. on-fly or for some reasons it could be duplicated diagnostics for the same factory
            //  see [PsiCheckerTestGenerated$Checker.testRedeclaration]
            if (diagnostic in highlightInfoByDiagnostic) continue

            // annotate diagnostics those were not possible to report (and therefore render) on-the-fly
            annotateDiagnostic(psiElement, holder, diagnostic, highlightInfoByDiagnostic, calculatingInProgress = false)
        }

        // apply quick fixes for all diagnostics grouping by element
        highlightInfoByDiagnostic.keys.groupBy { it.psiElement }.forEach {
            annotateQuickFixes(it.key, it.value, highlightInfoByDiagnostic)
        }
    }

    private fun annotateDiagnostic(
        element: PsiElement,
        holder: HighlightInfoHolder,
        diagnostic: Diagnostic,
        highlightInfoByDiagnostic: MutableMap<Diagnostic, HighlightInfo>? = null,
        highlightInfoByTextRange: MutableMap<TextRange, HighlightInfo>? = null,
        calculatingInProgress: Boolean = true
    ) = annotateDiagnostics(
        element,
        holder,
        listOf(diagnostic),
        highlightInfoByDiagnostic,
        highlightInfoByTextRange,
        calculatingInProgress
    )

    private fun cleanUpCalculatingAnnotations(highlightInfoByTextRange: Map<TextRange, HighlightInfo>) {
        highlightInfoByTextRange.values.forEach { annotation ->
            annotation.unregisterQuickFix {
                it is CalculatingIntentionAction
            }
        }
    }

    private fun annotateDiagnostics(
        element: PsiElement,
        holder: HighlightInfoHolder,
        diagnostics: List<Diagnostic>,
        highlightInfoByDiagnostic: MutableMap<Diagnostic, HighlightInfo>? = null,
        highlightInfoByTextRange: MutableMap<TextRange, HighlightInfo>? = null,
        calculatingInProgress: Boolean = false
    ) = annotateDiagnostics(
        element, holder, diagnostics, highlightInfoByDiagnostic, highlightInfoByTextRange, ::shouldSuppressUnusedParameter,
        noFixes = true,
        calculatingInProgress = calculatingInProgress
    )

    /**
     * [diagnostics] has to belong to the same element
     */
    private fun annotateQuickFixes(
        element: PsiElement,
        diagnostics: List<Diagnostic>,
        highlightInfoByDiagnostic: MutableMap<Diagnostic, HighlightInfo>
    ) {
        if (diagnostics.isEmpty()) return

        assertBelongsToTheSameElement(element, diagnostics)

        ElementAnnotator(element) { param ->
            shouldSuppressUnusedParameter(param)
        }.registerDiagnosticsQuickFixes(diagnostics, highlightInfoByDiagnostic)
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

        fun createQuickFixes(diagnostic: Diagnostic): Collection<IntentionAction> =
            createQuickFixes(listOfNotNull(diagnostic))[diagnostic]

        fun wasUnresolved(element: KtNameReferenceExpression) = element.getUserData(UNRESOLVED_KEY) != null

        internal fun assertBelongsToTheSameElement(element: PsiElement, diagnostics: Collection<Diagnostic>) {
            assert(diagnostics.all { it.psiElement == element })
        }

        fun annotateDiagnostics(
            element: PsiElement,
            holder: HighlightInfoHolder,
            diagnostics: Collection<Diagnostic>,
            highlightInfoByDiagnostic: MutableMap<Diagnostic, HighlightInfo>? = null,
            highlightInfoByTextRange: MutableMap<TextRange, HighlightInfo>? = null,
            shouldSuppressUnusedParameter: (KtParameter) -> Boolean = { false },
            noFixes: Boolean = false,
            calculatingInProgress: Boolean = false
        ) {
            if (diagnostics.isEmpty()) return
            element.getUserData(DO_NOT_HIGHLIGHT_KEY)?.let { return }

            assertBelongsToTheSameElement(element, diagnostics)

            if (element is KtNameReferenceExpression) {
                val unresolved = diagnostics.any { it.factory == Errors.UNRESOLVED_REFERENCE }
                element.putUserData(UNRESOLVED_KEY, if (unresolved) Unit else null)
            }

            ElementAnnotator(element) { param ->
                shouldSuppressUnusedParameter(param)
            }.registerDiagnosticsAnnotations(
                holder, diagnostics, highlightInfoByDiagnostic,
                highlightInfoByTextRange,
                noFixes = noFixes, calculatingInProgress = calculatingInProgress
            )
        }
    }
}

internal fun createQuickFixes(similarDiagnostics: Collection<Diagnostic>): MultiMap<Diagnostic, IntentionAction> {
    val first = similarDiagnostics.minByOrNull { it.toString() }
    val factory = similarDiagnostics.first().getRealDiagnosticFactory()

    val actions = MultiMap<Diagnostic, IntentionAction>()

    val intentionActionsFactories = QuickFixes.getInstance().getActionFactories(factory)
    for (intentionActionsFactory in intentionActionsFactories) {
        val allProblemsActions = intentionActionsFactory.createActionsForAllProblems(similarDiagnostics)
        if (allProblemsActions.isNotEmpty()) {
            actions.putValues(first, allProblemsActions)
        } else {
            for (diagnostic in similarDiagnostics) {
                actions.putValues(diagnostic, intentionActionsFactory.createActions(diagnostic))
            }
        }
    }

    for (diagnostic in similarDiagnostics) {
        actions.putValues(diagnostic, QuickFixes.getInstance().getActions(diagnostic.factory))
    }

    actions.values().forEach { NoDeclarationDescriptorsChecker.check(it::class.java) }

    return actions
}


private fun Diagnostic.getRealDiagnosticFactory(): DiagnosticFactory<*> =
    when (factory) {
        Errors.PLUGIN_ERROR -> Errors.PLUGIN_ERROR.cast(this).a.factory
        Errors.PLUGIN_WARNING -> Errors.PLUGIN_WARNING.cast(this).a.factory
        Errors.PLUGIN_INFO -> Errors.PLUGIN_INFO.cast(this).a.factory
        else -> factory
    }

private object NoDeclarationDescriptorsChecker {
    private val LOG = Logger.getInstance(NoDeclarationDescriptorsChecker::class.java)

    private val checkedQuickFixClasses = Collections.synchronizedSet(HashSet<Class<*>>())

    fun check(quickFixClass: Class<*>) {
        if (!checkedQuickFixClasses.add(quickFixClass)) return

        for (field in quickFixClass.declaredFields) {
            checkType(field.genericType, field)
        }

        quickFixClass.superclass?.let { check(it) }
    }

    private fun checkType(type: Type, field: Field) {
        when (type) {
            is Class<*> -> {
                if (DeclarationDescriptor::class.java.isAssignableFrom(type) || KotlinType::class.java.isAssignableFrom(type)) {
                    LOG.error(
                        "QuickFix class ${field.declaringClass.name} contains field ${field.name} that holds ${type.simpleName}. "
                                + "This leads to holding too much memory through this quick-fix instance. "
                                + "Possible solution can be wrapping it using KotlinIntentionActionFactoryWithDelegate."
                    )
                }

                if (IntentionAction::class.java.isAssignableFrom(type)) {
                    check(type)
                }

            }

            is GenericArrayType -> checkType(type.genericComponentType, field)

            is ParameterizedType -> {
                if (Collection::class.java.isAssignableFrom(type.rawType as Class<*>)) {
                    type.actualTypeArguments.forEach { checkType(it, field) }
                }
            }

            is WildcardType -> type.upperBounds.forEach { checkType(it, field) }
        }
    }
}

