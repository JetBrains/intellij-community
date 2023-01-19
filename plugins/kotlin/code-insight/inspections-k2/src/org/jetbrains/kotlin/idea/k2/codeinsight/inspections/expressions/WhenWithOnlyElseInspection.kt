// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

/**
 * This inspection finds and replaces `when` expressions containing only a single `else` branch by the body of that branch, accounting for
 * the presence of a subject variable. In general, it rewrites:
 *
 * ```kotlin
 *   when (val x = e1) {
 *     else -> e2
 *   }
 * ```
 *
 * into:
 *
 * ```
 *   run {
 *     val x = e1
 *     e2
 *   }
 * ```
 *
 * contingent on a few complications. See Steps 3.1 and 3.2 below.
 */
internal class WhenWithOnlyElseInspection
    : AbstractKotlinApplicableInspectionWithContext<KtWhenExpression, WhenWithOnlyElseInspection.Context>(KtWhenExpression::class) {

    data class WhenSubjectVariableInfo(
        val subjectVariable: SmartPsiElementPointer<KtProperty>,
        val initializer: SmartPsiElementPointer<KtExpression>?,
        val isInitializerPure: Boolean
    )

    class Context(
        val isWhenUsedAsExpression: Boolean,
        val elseExpression: SmartPsiElementPointer<KtExpression>,
        val subjectVariableInfo: WhenSubjectVariableInfo?
    )

    override fun getProblemDescription(element: KtWhenExpression, context: Context): String =
        KotlinBundle.message("inspection.when.with.only.else.display.name")

    override fun getActionFamilyName(): String = KotlinBundle.message("inspection.when.with.only.else.action.name")

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtWhenExpression> = ApplicabilityRanges.SELF

    /**
     * STEP 1:
     * Discard `when` expressions that are not of the form
     * ```kotlin
     * when (...) { else -> ... }
     * ```
     */
    override fun isApplicableByPsi(element: KtWhenExpression): Boolean = element.entries.singleOrNull()?.isElse == true

    /**
     * STEP 2:
     * Gather the necessary semantic information for the transformation:
     *   - whether the `when` expression itself is used as an expression
     *   - for the subject variable, if present, whether the initializer is pure.
     */
    context(KtAnalysisSession)
    override fun prepareContext(element: KtWhenExpression): Context? {
        val singleEntry = element.entries.singleOrNull() ?: return null
        val elseExpression = singleEntry.takeIf { it.isElse }?.expression ?: return null
        val isWhenUsedAsExpression = element.isUsedAsExpression()
        val subjectVariableInfo = element.subjectVariable?.let {
            val initializer = it.initializer
            WhenSubjectVariableInfo(
                subjectVariable = it.createSmartPointer(),
                initializer = initializer?.createSmartPointer(),
                isInitializerPure = initializer?.isPure() == true
            )
        }

        return Context(isWhenUsedAsExpression, elseExpression.createSmartPointer(), subjectVariableInfo)
    }

    override fun apply(element: KtWhenExpression, context: Context, project: Project, editor: Editor?) {
        if (editor == null) return

        val factory = KtPsiFactory(project)
        val newCaretPosition = element.startOffset

        val (rewrittenBranch, insertedCallToKotlinDotRun) = rewriteElseBranch(context, factory) ?: return
        element.replaceWithRewrittenBranch(rewrittenBranch, insertedCallToKotlinDotRun, context, factory)

        editor.caretModel.moveToOffset(newCaretPosition)
    }

    /**
     * STEP 3.1:
     * Rewrite the `else` branch body to include the subject variable, if present:
     *
     *   - if the variable is unused and the initializer is pure, elide the variable entirely
     *   - if the variable is only used once and the initializer is pure, inline the variable in the branch body
     *   - otherwise, preface the branch body with the subject variable declaration and wrap it in a call to `kotlin.run`.
     *
     *  Additionally, track if _we_ inserted a call to `kotlin.run`. In that case, we will attempt to shorten the reference to `run`,
     *  once we have done the actual transformation in Step 3.2 below. This preserves explicit, user-supplied calls to `kotlin.run`.
     */
    private fun rewriteElseBranch(context: Context, factory: KtPsiFactory): Pair<KtExpression, Boolean>? {
        val elseExpression = context.elseExpression.dereference() ?: return null
        val info = context.subjectVariableInfo

        if (info?.initializer == null) {
            return elseExpression to false
        }

        val subjectVariable = info.subjectVariable.dereference() ?: return null
        val initializer = info.initializer.dereference() ?: return null
        val isInitializerPure = info.isInitializerPure
        val references = ReferencesSearch.search(subjectVariable).findAll()
        val occurrences = references.size
        return when {
            occurrences == 0 && isInitializerPure ->
                elseExpression to false

            occurrences == 1 && isInitializerPure -> {
                references.single().element.replace(initializer)
                elseExpression to false
            }

            else -> {
                if (elseExpression is KtBlockExpression) {
                    elseExpression.apply {
                        val addedSubjectVariable = addBefore(subjectVariable, statements.firstOrNull())
                        addAfter(factory.createNewLine(), addedSubjectVariable)
                    }
                    factory.createExpressionByPattern("kotlin.run $0", elseExpression.text) to true
                } else {
                    factory.createExpressionByPattern("kotlin.run { $0\n$1 }", subjectVariable.text, elseExpression.text) to true
                }
            }
        }
    }

    /**
     * Step 3.2:
     * Replace the `when` expression with the (possibly rewritten) branch:
     *
     *   - If the branch is a single expression, either originally, or due to the rewrite to a `kotlin.run { ... }` by step 3.1, replace
     *     the `when` by the expression.
     *   - Otherwise, the else branch is of the form `else -> { ... }` and we have to decide:
     *       - If the `when` is used as an expression, replace the `when` by a call to `kotlin.run { ... }`
     *       - Otherwise, inline the statements of `{ ... }` in place of the `when` expression.
     *
     *  Generated calls to `kotlin.run` are shortened to `run`, if possible.
     */
    private fun KtWhenExpression.replaceWithRewrittenBranch(
        rewrittenBranch: KtExpression,
        insertedCallToKotlinDotRun: Boolean,
        context: Context,
        factory: KtPsiFactory,
    ) {
        when {
            rewrittenBranch !is KtBlockExpression ->
                this.replace(rewrittenBranch).also {
                    if (insertedCallToKotlinDotRun) it.shortenKotlinDotRun()
                }

            context.isWhenUsedAsExpression ->
                this.replace(factory.createExpressionByPattern("kotlin.run $0", rewrittenBranch.text)).also {
                    it.shortenKotlinDotRun()
                }

            else -> {
                val firstChildSibling = rewrittenBranch.firstChild.nextSibling
                val lastChild = rewrittenBranch.lastChild
                this.parent.addRangeAfter(firstChildSibling, lastChild.prevSibling, this).also {
                    this.delete()
                }
            }
        }
    }

    /**
     * Over-approximates if the expression has side-effects.
     *
     * @return `true` if `this` is _definitely_ pure.
     */
    context(KtAnalysisSession)
    private fun KtExpression.isPure(): Boolean = when (this) {
        is KtStringTemplateExpression -> !hasInterpolation()
        is KtConstantExpression -> true
        is KtIsExpression -> true
        is KtThisExpression -> true
        is KtObjectLiteralExpression -> true
        else ->
            evaluate(KtConstantEvaluationMode.CONSTANT_EXPRESSION_EVALUATION) != null
    }

    /**
     * Shortens calls to `kotlin.run`, as generated by the transformation in this inspection, to `run` if possible, cf. the logic of
     * [shortenReferencesInRange].
     */
    private fun PsiElement.shortenKotlinDotRun() {
        if (this !is KtDotQualifiedExpression) return
        shortenReferencesInRange(
            this.containingKtFile,
            TextRange(
                this.startOffset,
                this.startOffset + "kotlin.run".length
            )
        )
    }
}
