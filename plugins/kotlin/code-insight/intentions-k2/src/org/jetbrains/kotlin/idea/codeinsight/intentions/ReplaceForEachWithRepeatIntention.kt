// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.intentions.ForLoopUtils.computeReturnsToReplace
import org.jetbrains.kotlin.idea.codeinsight.intentions.ForLoopUtils.ReturnsToReplace
import org.jetbrains.kotlin.idea.codeinsight.intentions.ForLoopUtils.isZeroBasedRange
import org.jetbrains.kotlin.idea.codeinsight.intentions.ForLoopUtils.relabelReturns
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

private val REPEAT_KEYWORD: Name = Name.identifier("repeat")
private val FOR_EACH_NAME: Name = StandardKotlinNames.For.forEachName

private val FOR_EACH_CALLABLE_IDS: Set<CallableId> = setOf(
    CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, FOR_EACH_NAME),
    CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE.child(Name.identifier("sequences")), FOR_EACH_NAME),
    CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE.child(Name.identifier("ranges")), FOR_EACH_NAME),
)

/**
 * Replaces `(0..<n).forEach { ... }` / `(0 until n).forEach { ... }` with `repeat(n) { ... }`.
 *
 * Mirrors [ReplaceForLoopWithRepeatIntention] but starts from a `forEach` call over a zero-based range.
 */
internal class ReplaceForEachWithRepeatIntention :
    KotlinApplicableModCommandAction<KtCallExpression, ReplaceForEachWithRepeatIntention.Context>(KtCallExpression::class) {

    data class Context(
        val times: String,
        val returnsToRelabel: ReturnsToReplace,
    )

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.foreach.with.repeat")

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        if (element.getCallNameExpression()?.getReferencedName() != FOR_EACH_NAME.asString()) return false

        val qualified = element.getQualifiedExpressionForSelector() as? KtDotQualifiedExpression ?: return false
        val receiver = KtPsiUtil.safeDeparenthesize(qualified.receiverExpression) as? KtBinaryExpression ?: return false
        if (receiver.left?.text != "0") return false

        val lambda = element.singleLambdaArgument() ?: return false
        return lambda.bodyExpression != null && lambda.valueParameters.size <= 1
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val callee = element.calleeExpression?.mainReference?.resolveToSymbol() as? KaNamedFunctionSymbol ?: return null
        if (callee.callableId !in FOR_EACH_CALLABLE_IDS) return null

        val qualified = element.getQualifiedExpressionForSelector() as? KtDotQualifiedExpression ?: return null
        val range = KtPsiUtil.safeDeparenthesize(qualified.receiverExpression) as? KtBinaryExpression ?: return null
        if (!range.isZeroBasedRange()) return null

        val times = range.right?.text ?: return null
        val lambda = element.singleLambdaArgument() ?: return null

        return Context(times, lambda.computeReturnsToReplace())
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallExpression,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        val qualified = element.getQualifiedExpressionForSelector() as? KtDotQualifiedExpression ?: return
        val outerLabel = qualified.parent as? KtLabeledExpression
        val target: KtExpression = outerLabel ?: qualified
        val commentSaver = CommentSaver(target)
        val factory = KtPsiFactory(element.project)
        val lambda = element.singleLambdaArgument() ?: return

        val innerLabel = (element.valueArguments.singleOrNull()?.getArgumentExpression() as? KtLabeledExpression)
            ?.getLabelName()
        val userLabel = outerLabel?.getLabelName() ?: innerLabel
        val labelName = userLabel ?: REPEAT_KEYWORD.asString()
        val needLabel = relabelReturns(elementContext.returnsToRelabel, lambda, labelName, factory)
        val labelPart = if (userLabel != null || needLabel) "$labelName@ " else ""

        val replacement = factory.createExpressionByPattern(
            "repeat($0, $labelPart$1)", elementContext.times, lambda,
        )

        val result = target.replaced(replacement) as? KtCallExpression ?: return
        result.moveFunctionLiteralOutsideParentheses(updater::moveCaretTo)
        commentSaver.restore(result)
    }

    private fun KtCallExpression.singleLambdaArgument(): KtLambdaExpression? {
        val arg = valueArguments.singleOrNull()?.getArgumentExpression() ?: return null
        return arg as? KtLambdaExpression ?: (arg as? KtLabeledExpression)?.baseExpression as? KtLambdaExpression
    }
}