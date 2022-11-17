// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.*

/**
 * This inspection finds and replaces When expressions containing only a single
 * `else` branch by the body of that branch, accounting for the presence of a
 * subject variable. In general it rewrites:
 * ```kotlin
 *   when (val x = e1) {
 *     else -> e2
 *   }
 * ```
 * into:
 * ```
 *   run {
 *     val x = e1
 *     e2
 *   }
 * ```
 * contingent on a few complications. See Steps 3.1 and 3.2 below.
 */
internal class WhenWithOnlyElseInspection: AbstractKotlinApplicatorBasedInspection<KtWhenExpression, WhenWithOnlyElseInspection.Input>(KtWhenExpression::class) {

    data class WhenSubjectVariableInfo(
        val subjectVariable: KtProperty,
        val initializer: KtExpression?,
        val isInitializerPure: Boolean
    )

    class Input(
        val isWhenUsedAsExpression: Boolean,
        val elseExpression: KtExpression,
        val subjectVariableInfo: WhenSubjectVariableInfo?
    ) : KotlinApplicatorInput

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtWhenExpression> = ApplicabilityRanges.SELF

    /**
     * STEP 2:
     * Gather the necessary semantic information for the transformation:
     *   - whether the when expression itself is used as an expression
     *   - for the subject variable, if present, whether the initializer is pure.
     */
    override fun getInputProvider(): KotlinApplicatorInputProvider<KtWhenExpression, Input> = inputProvider { whenExpression ->
        val singleEntry = whenExpression.entries.singleOrNull() ?: return@inputProvider null
        val elseExpression = singleEntry.takeIf { it.isElse }?.expression ?: return@inputProvider null
        val isWhenUsedAsExpression = whenExpression.isUsedAsExpression()
        val subjectVariableInfo = whenExpression.subjectVariable?.let {
            val initializer = it.initializer
            WhenSubjectVariableInfo(
                subjectVariable = it,
                initializer = initializer,
                isInitializerPure = initializer?.isPure() == true
            )
        }

        Input(isWhenUsedAsExpression, elseExpression, subjectVariableInfo)
    }

    /**
     * Over-approximates if the expression has side-effects.
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

    override fun getApplicator(): KotlinApplicator<KtWhenExpression, Input> = applicator {
        familyAndActionName(KotlinBundle.lazyMessage("inspection.when.with.only.else.display.name"))

        /**
         * STEP 1:
         * Discard when expressions that are not of the form
         *```kotlin
         * when (...) { else -> ... }
         * ```
         */
        isApplicableByPsi { whenExpression ->
            whenExpression.entries.singleOrNull()?.isElse == true
        }

        applyToWithEditorRequired { whenExpression, input, _, editor ->
            val factory = KtPsiFactory(whenExpression)
            val newCaretPosition = whenExpression.startOffset

            /**
             * STEP 3.1:
             * Rewrite the branch body to include the subject variable, if
             * present:
             *
             *   - if the variable is unused and the initializer is pure, elide
             *     the variable entirely
             *   - if the variable is only used once and the initializer is
             *     pure, inline the variable in the branch body
             *   - otherwise, preface the branch body with the subject variable
             *     declaration and wrap it in a call to `kotlin.run`.
             *
             *  Additionally, track if _we_ inserted a call to `kotlin.run`. In
             *  that case, we will attempt to shorten the reference to `run`,
             *  once we have done the actual transformation in Step 3.2 below.
             *  This preserves explicit, user-supplied calls to `kotlin.run`.
             */
            val (rewrittenBranch, insertedCallToKotlinDotRun) = if (input.subjectVariableInfo?.initializer == null) {
                input.elseExpression to false
            } else {
                val info = input.subjectVariableInfo
                val isInitializerPure = info.isInitializerPure
                val references = ReferencesSearch.search(info.subjectVariable).findAll()
                val occurrences = references.size
                when {
                    occurrences == 0 && isInitializerPure ->
                        input.elseExpression to false

                    occurrences == 1 && isInitializerPure -> {
                        references.single().element.replace(info.initializer!!)
                        input.elseExpression to false
                    }

                    else -> {
                        val branch = input.elseExpression
                        if (branch is KtBlockExpression) {
                            branch.apply {
                                val subjectVariable = addBefore(info.subjectVariable, statements.firstOrNull())
                                addAfter(factory.createNewLine(), subjectVariable)
                            }
                            factory.createExpressionByPattern("kotlin.run $0", branch.text) to true
                        } else {
                            factory.createExpressionByPattern("kotlin.run { $0\n$1 }", info.subjectVariable.text, branch.text) to true
                        }
                    }
                }
            }

            /**
             * Step 3.2:
             * Replace the when expression with the (possibly rewritten) branch:
             *
             *   - If the branch is a single expression, either originally, or
             *     due to the rewrite to a `kotlin.run { ... }` by step 3.1, replace
             *     the when by the expression.
             *   - Otherwise, the else branch is of the form `else -> { ... }`
             *     and we have to decide
             *       + If the when is used as an expression, replace the when by
             *         a call to `kotlin.run { ... }`
             *       + Otherwise, inline the statements of `{ ... }` in place of
             *         the when expression.
             *
             *  Generated calls to `kotlin.run` are shortened to `run`, if possible.
             */
            when {
                rewrittenBranch !is KtBlockExpression ->
                    whenExpression.replace(rewrittenBranch).also {
                        if (insertedCallToKotlinDotRun) it.shortenKotlinDotRun()
                    }

                input.isWhenUsedAsExpression ->
                    whenExpression.replace(factory.createExpressionByPattern("kotlin.run $0", rewrittenBranch.text)).also {
                        it.shortenKotlinDotRun()
                    }

                else -> {
                    val firstChildSibling = rewrittenBranch.firstChild.nextSibling
                    val lastChild = rewrittenBranch.lastChild
                    whenExpression.parent.addRangeAfter(firstChildSibling, lastChild.prevSibling, whenExpression).also {
                        whenExpression.delete()
                    }
                }
            }

            editor.caretModel.moveToOffset(newCaretPosition)
        }
    }

    /**
     * Shortens calls to `kotlin.run`, as generated by the transformation in
     * this inspection, to `run` if possible, cf. the logic of
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
