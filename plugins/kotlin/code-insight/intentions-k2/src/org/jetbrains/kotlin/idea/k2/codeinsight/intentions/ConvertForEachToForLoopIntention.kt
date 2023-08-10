// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.ImplicitReceiverInfo
import org.jetbrains.kotlin.idea.codeinsight.utils.dereferenceValidPointers
import org.jetbrains.kotlin.idea.codeinsight.utils.getImplicitReceiverInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.core.AbstractKotlinNameSuggester
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.renderer.render

private val FOR_EACH_NAME: Name = Name.identifier("forEach")

private val FOR_EACH_CALLABLE_IDS: Set<CallableId> = setOf(
    CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, FOR_EACH_NAME),
    CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE.child(Name.identifier("sequences")), FOR_EACH_NAME),
    CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE.child(Name.identifier("text")), FOR_EACH_NAME),
)

private val FOR_EACH_INDEXED_NAME: Name = Name.identifier("forEachIndexed")

private val FOR_EACH_INDEXED_CALLABLE_IDS: Set<CallableId> = setOf(
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
        commentSaver.restore(result)

        if (editor == null) return

        if (result is KtLabeledExpression) {
            editor.caretModel.moveToOffset(result.startOffset)
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
            KotlinVariableInplaceRenameHandler().doRename(result, editor, null)
        } else {
            val forExpression = result as? KtForExpression ?: result.collectDescendantsOfType<KtForExpression>().first()
            forExpression.loopParameter?.let { editor.caretModel.moveToOffset(it.startOffset) }
        }
    }

    private fun generateLoop(
        receiver: KtExpression?,
        lambda: KtLambdaExpression,
        isForEachIndexed: Boolean,
        context: Context
    ): KtExpression? {
        val factory = KtPsiFactory(lambda.project)
        val body = lambda.bodyExpression ?: return null

        val loopLabelName by lazy { suggestLoopName(lambda) }
        var needLoopLabel = false

        for (returnExpr in context.returnsToReplace.dereferenceValidPointers()) {
            val parentLoop = returnExpr.getStrictParentOfType<KtLoopExpression>()
            if (parentLoop?.getStrictParentOfType<KtLambdaExpression>() == lambda) {
                returnExpr.replace(factory.createExpression("continue@$loopLabelName"))
                needLoopLabel = true
            } else {
                returnExpr.replace(factory.createExpression("continue"))
            }
        }

        val loopRange = getLoopRange(receiver, context, factory) ?: return null
        val loopLabel = if (needLoopLabel) "$loopLabelName@ " else ""
        val loop = createLoopExpression(loopLabel, loopRange, lambda.valueParameters, body, isForEachIndexed, factory)

        return if (loopRange.getQualifiedExpressionForReceiver() is KtSafeQualifiedExpression) {
            factory.createExpressionByPattern("if ($0 != null) { $1 }", loopRange, loop)
        } else {
            loop
        }
    }

    private fun suggestLoopName(lambda: KtLambdaExpression): String =
        KotlinNameSuggester.suggestNameByName("loop") { candidate ->
            !lambda.anyDescendantOfType<KtLabeledExpression> { it.getLabelName() == candidate }
        }

    private fun getLoopRange(receiver: KtExpression?, context: Context, factory: KtPsiFactory): KtExpression? {
        return if (receiver != null) {
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
    }

    private fun createLoopExpression(
        loopLabel: String,
        loopRange: KtExpression,
        parameters: List<KtParameter>,
        body: KtBlockExpression,
        isForEachIndexed: Boolean,
        factory: KtPsiFactory
    ): KtExpression = if (isForEachIndexed) {
        val loopRangeWithIndex = if (loopRange is KtThisExpression && loopRange.labelQualifier == null) {
            factory.createExpression("withIndex()")
        } else {
            factory.createExpressionByPattern("$0.withIndex()", loopRange)
        }

        factory.createExpressionByPattern(
            "${loopLabel}for(($0, $1) in $2){ $3 }",
            parameters[0].text,
            parameters[1].text,
            loopRangeWithIndex,
            body.allChildren
        )
    } else {
        factory.createExpressionByPattern(
            "${loopLabel}for($0 in $1){ $2 }",
            parameters.singleOrNull() ?: StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier,
            loopRange,
            body.allChildren
        )
    }
}
