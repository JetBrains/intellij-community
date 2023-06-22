// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.ImplicitReceiverInfo
import org.jetbrains.kotlin.idea.codeinsight.utils.dereferenceValidPointers
import org.jetbrains.kotlin.idea.codeinsight.utils.getImplicitReceiverInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.renderer.render

private val FOR_EACH_NAME = Name.identifier("forEach")

private val FOR_EACH_CALLABLE_IDS = setOf(
    CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, FOR_EACH_NAME),
    CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE.child(Name.identifier("sequences")), FOR_EACH_NAME),
    CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE.child(Name.identifier("text")), FOR_EACH_NAME),
)

private val FOR_EACH_INDEXED_NAME = Name.identifier("forEachIndexed")

private val FOR_EACH_INDEXED_CALLABLE_IDS = setOf(
    CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, FOR_EACH_INDEXED_NAME),
    CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE.child(Name.identifier("sequences")), FOR_EACH_INDEXED_NAME),
    CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE.child(Name.identifier("text")), FOR_EACH_INDEXED_NAME),
)

private typealias ReturnsToReplace = List<SmartPsiElementPointer<KtReturnExpression>>

internal class ConvertForEachToForLoopIntention
    : AbstractKotlinApplicableIntentionWithContext<KtCallExpression, ConvertForEachToForLoopIntention.Context>(
        KtCallExpression::class
    ) {

    class Context(
        /** Caches the [KtReturnExpression]s which need to be replaced with `continue`. */
        val returnsToReplace: ReturnsToReplace,
        val implicitReceiverInfo: ImplicitReceiverInfo?,
    )

    override fun getFamilyName(): String = KotlinBundle.message("replace.with.a.for.loop")
    override fun getActionName(element: KtCallExpression, context: Context): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtCallExpression> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val referencedName = element.getCallNameExpression()?.getReferencedName()
        val isForEach = referencedName == FOR_EACH_NAME.asString()
        val isForEachIndexed = referencedName == FOR_EACH_INDEXED_NAME.asString()
        if (!isForEach && !isForEachIndexed) return false

        val lambdaArgument = element.getSingleLambdaArgument() ?: return false
        val valueParameterSize = lambdaArgument.valueParameters.size
        if (isForEach && valueParameterSize > 1 || isForEachIndexed && valueParameterSize != 2) return false
        return lambdaArgument.bodyExpression != null
    }

    private fun KtCallExpression.getSingleLambdaArgument(): KtLambdaExpression? =
        valueArguments.singleOrNull()?.getArgumentExpression() as? KtLambdaExpression

    context(KtAnalysisSession)
    override fun prepareContext(element: KtCallExpression): Context? {
        if (!element.isForEachByAnalyze()) return null
        if (element.isUsedAsExpression()) return null

        val returnsToReplace = computeReturnsToReplace(element) ?: return null

        val receiver = element.getQualifiedExpressionForSelector()?.receiverExpression
        val implicitReceiverInfo = if (receiver == null) {
            element.getImplicitReceiverInfo() ?: return null
        } else null

        return Context(returnsToReplace, implicitReceiverInfo)
    }

    context(KtAnalysisSession)
    private fun KtCallExpression.isForEachByAnalyze(): Boolean {
        val symbol = calleeExpression?.mainReference?.resolveToSymbol() as? KtFunctionSymbol ?: return false
        val callableId = symbol.callableIdIfNonLocal
        return callableId in FOR_EACH_CALLABLE_IDS || callableId in FOR_EACH_INDEXED_CALLABLE_IDS
    }

    context(KtAnalysisSession)
    private fun computeReturnsToReplace(element: KtCallExpression): ReturnsToReplace? {
        val lambda = element.getSingleLambdaArgument() ?: return null
        val lambdaBody = lambda.bodyExpression ?: return null
        val functionLiteralSymbol = lambda.functionLiteral.getSymbol()
        return buildList {
            lambdaBody.forEachDescendantOfType<KtReturnExpression> { returnExpression ->
                if (returnExpression.getReturnTargetSymbol() == functionLiteralSymbol) {
                    add(returnExpression.createSmartPointer())
                }
            }
        }
    }

    override fun apply(element: KtCallExpression, context: Context, project: Project, editor: Editor?) {
        val qualifiedExpression = element.getQualifiedExpressionForSelector()
        val receiverExpression = qualifiedExpression?.receiverExpression
        val targetExpression = qualifiedExpression ?: element
        val commentSaver = CommentSaver(targetExpression)

        val lambda = element.getSingleLambdaArgument() ?: return
        val isForEachIndexed =  element.getCallNameExpression()?.getReferencedName() == FOR_EACH_INDEXED_NAME.asString()
        val loop = generateLoop(receiverExpression, lambda, isForEachIndexed, context) ?: return
        val result = targetExpression.replace(loop)
        val forExpression = result as? KtForExpression ?: result.collectDescendantsOfType<KtForExpression>().first()
        forExpression.loopParameter?.let { editor?.caretModel?.moveToOffset(it.startOffset) }

        commentSaver.restore(result)
    }

    private fun generateLoop(
        receiver: KtExpression?,
        lambda: KtLambdaExpression,
        isForEachIndexed: Boolean,
        context: Context
    ): KtExpression? {
        val factory = KtPsiFactory(lambda)
        val body = lambda.bodyExpression ?: return null

        val returnsToReplace = context.returnsToReplace.dereferenceValidPointers()
        returnsToReplace.forEach { it.replace(factory.createExpression("continue")) }

        val loopRange = if (receiver != null) {
            KtPsiUtil.safeDeparenthesize(receiver)
        } else {
            val implicitReceiverInfo = context.implicitReceiverInfo ?: return null
            if (implicitReceiverInfo.isUnambiguousLabel) {
                factory.createThisExpression()
            } else {
                val label = implicitReceiverInfo.receiverLabel ?: return null
                factory.createThisExpression(label.render())
            }
        }

        val parameters = lambda.valueParameters
        val loop = if (isForEachIndexed) {
            val loopRangeWithIndex = if (loopRange is KtThisExpression && loopRange.labelQualifier == null) {
                factory.createExpression("withIndex()")
            } else {
                factory.createExpressionByPattern("$0.withIndex()", loopRange)
            }
            factory.createExpressionByPattern(
                "for(($0, $1) in $2){ $3 }",
                parameters[0].text,
                parameters[1].text,
                loopRangeWithIndex,
                body.allChildren
            )
        } else {
            factory.createExpressionByPattern(
                "for($0 in $1){ $2 }",
                parameters.singleOrNull() ?: "it",
                loopRange,
                body.allChildren
            )
        }

        return if (loopRange.getQualifiedExpressionForReceiver() is KtSafeQualifiedExpression) {
            factory.createExpressionByPattern("if ($0 != null) { $1 }", loopRange, loop)
        } else {
            loop
        }
    }
}
