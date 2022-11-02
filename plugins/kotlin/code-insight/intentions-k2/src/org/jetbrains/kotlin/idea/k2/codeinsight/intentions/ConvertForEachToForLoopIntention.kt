// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.utils.dereferenceValidPointers
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

private const val FOR_EACH_NAME = "forEach"

private val FOR_EACH_FQ_NAMES: Set<String> by lazy {
    sequenceOf("collections", "sequences", "text").map { "kotlin.$it.$FOR_EACH_NAME" }.toSet()
}

private typealias ReturnsToReplace = Set<SmartPsiElementPointer<KtReturnExpression>>

internal class ConvertForEachToForLoopIntention
    : KotlinApplicableIntentionWithContext<KtCallExpression, ConvertForEachToForLoopIntention.Context>(
        KtCallExpression::class
    ) {

    class Context(
        /** Caches the [KtReturnExpression]s which need to be replaced with `continue`. */
        val returnsToReplace: ReturnsToReplace,
    )

    override fun getFamilyName(): String = KotlinBundle.message("replace.with.a.for.loop")
    override fun getActionName(element: KtCallExpression, context: Context): String = familyName

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        if (element.getCallNameExpression()?.getReferencedName() != FOR_EACH_NAME) return false

        // We only want to convert calls of the form `c.forEach { ... }`, so the parent of `forEach { ... }` must be a
        // KtDotQualifiedExpression.
        if (element.parent !is KtDotQualifiedExpression) return false

        val lambdaArgument = element.getSingleLambdaArgument() ?: return false
        return lambdaArgument.valueParameters.size <= 1 && lambdaArgument.bodyExpression != null
    }

    private fun KtCallExpression.getSingleLambdaArgument(): KtLambdaExpression? =
        valueArguments.singleOrNull()?.getArgumentExpression() as? KtLambdaExpression

    context(KtAnalysisSession)
    override fun prepareContext(element: KtCallExpression): Context? {
        if (!element.isForEachByFqName()) return null
        return computeReturnsToReplace(element)?.let { Context(it) }
    }

    context(KtAnalysisSession)
    private fun KtCallExpression.isForEachByFqName(): Boolean {
        val symbol = resolveCall().successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol as? KtFunctionSymbol ?: return false
        val fqName = symbol.callableIdIfNonLocal?.asSingleFqName() ?: return false
        return fqName.toString() in FOR_EACH_FQ_NAMES
    }

    context(KtAnalysisSession)
    private fun computeReturnsToReplace(element: KtCallExpression): ReturnsToReplace? = buildSet {
        val lambda = element.getSingleLambdaArgument() ?: return null
        val lambdaBody = lambda.bodyExpression ?: return null
        val functionLiteral = lambda.functionLiteral
        lambdaBody.forEachDescendantOfType<KtReturnExpression> {
            if (it.getReturnTargetSymbol() == functionLiteral.getSymbol()) {
                add(it.createSmartPointer())
            }
        }
    }

    override fun apply(element: KtCallExpression, context: Context, project: Project, editor: Editor?) {
        val dotQualifiedExpression = element.parent as? KtDotQualifiedExpression ?: return
        val commentSaver = CommentSaver(dotQualifiedExpression)

        val lambda = element.getSingleLambdaArgument() ?: return
        val loop = generateLoop(dotQualifiedExpression.receiverExpression, lambda, context) ?: return
        val result = dotQualifiedExpression.replace(loop) as KtForExpression
        result.loopParameter?.let { editor?.caretModel?.moveToOffset(it.startOffset) }

        commentSaver.restore(result)
    }

    private fun generateLoop(receiver: KtExpression, lambda: KtLambdaExpression, context: Context): KtForExpression? {
        val factory = KtPsiFactory(lambda)

        val body = lambda.bodyExpression ?: return null
        val returnsToReplace = context.returnsToReplace.dereferenceValidPointers()
        body.forEachDescendantOfType<KtReturnExpression> {
            if (returnsToReplace.contains(it)) {
                it.replace(factory.createExpression("continue"))
            }
        }

        val loopRange = KtPsiUtil.safeDeparenthesize(receiver)
        val parameter = lambda.valueParameters.singleOrNull()
        return factory.createExpressionByPattern(
            "for($0 in $1){ $2 }",
            parameter ?: "it",
            loopRange,
            body.allChildren
        ) as? KtForExpression
    }
}
