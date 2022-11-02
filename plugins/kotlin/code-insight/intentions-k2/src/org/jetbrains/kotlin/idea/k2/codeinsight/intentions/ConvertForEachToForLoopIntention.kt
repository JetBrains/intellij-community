// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.utils.dereferenceValidPointers
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

private val FOR_EACH_NAME = Name.identifier("forEach")

private val FOR_EACH_CALLABLE_IDS = setOf(
    CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, FOR_EACH_NAME),
    CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE.child(Name.identifier("sequences")), FOR_EACH_NAME),
    CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE.child(Name.identifier("text")), FOR_EACH_NAME),
)

private typealias ReturnsToReplace = List<SmartPsiElementPointer<KtReturnExpression>>

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
        if (element.getCallNameExpression()?.getReferencedName() != FOR_EACH_NAME.asString()) return false

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
        if (!element.isForEachByAnalyze()) return null
        return computeReturnsToReplace(element)?.let { Context(it) }
    }

    context(KtAnalysisSession)
    private fun KtCallExpression.isForEachByAnalyze(): Boolean {
        val symbol = calleeExpression?.mainReference?.resolveToSymbol() as? KtFunctionSymbol ?: return false
        return symbol.callableIdIfNonLocal in FOR_EACH_CALLABLE_IDS
    }

    context(KtAnalysisSession)
    private fun computeReturnsToReplace(element: KtCallExpression): ReturnsToReplace? {
        val lambda = element.getSingleLambdaArgument() ?: return null
        val lambdaBody = lambda.bodyExpression ?: return null
        val functionLiteral = lambda.functionLiteral
        return buildList {
            lambdaBody.forEachDescendantOfType<KtReturnExpression> { returnExpression ->
                if (returnExpression.getReturnTargetSymbol() == functionLiteral.getSymbol()) {
                    add(returnExpression.createSmartPointer())
                }
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
        returnsToReplace.forEach { it.replace(factory.createExpression("continue")) }

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
