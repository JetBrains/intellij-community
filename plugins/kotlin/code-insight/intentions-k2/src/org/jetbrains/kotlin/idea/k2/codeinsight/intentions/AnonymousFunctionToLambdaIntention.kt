// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.k2.refactoring.moveFunctionLiteralOutsideParenthesesIfPossible
import org.jetbrains.kotlin.idea.refactoring.getLastLambdaExpression
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.contentRange
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

class AnonymousFunctionToLambdaIntention : KotlinApplicableModCommandAction<KtNamedFunction, KtExpression>(KtNamedFunction::class) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("convert.anonymous.function.to.lambda.expression")

    override fun getApplicableRanges(element: KtNamedFunction): List<TextRange> {
        if (element.name != null || !element.hasBody()) return emptyList()
        return ApplicabilityRange.single(element) { it.funKeyword }
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtNamedFunction,
        elementContext: KtExpression,
        updater: ModPsiUpdater
    ) {
        val commentSaver = CommentSaver(element)
        val returnSaver = ReturnSaver(element)
        val argument = element.getStrictParentOfType<KtValueArgument>()
        val callElement = argument?.getStrictParentOfType<KtCallElement>()
        val replaced = element.replaced(elementContext)
        if (callElement != null) {
            val callExpression = replaced.parents.firstIsInstance<KtCallExpression>()
            val callee = callExpression.getCalleeExpressionIfAny() as? KtNameReferenceExpression ?: return

            val labeledExpression = replaced as? KtLabeledExpression
            val returnLabel = labeledExpression?.getLabelNameAsName() ?: callee.getReferencedNameAsName()
            val lambda = (labeledExpression?.baseExpression ?: replaced) as KtLambdaExpression
            returnSaver.restore(lambda, returnLabel)
            commentSaver.restore(replaced, forceAdjustIndent = true)

            callExpression.getLastLambdaExpression()?.moveFunctionLiteralOutsideParenthesesIfPossible()
        } else {
            val labeledExpression = replaced as? KtLabeledExpression ?: return
            val lambdaExpression = labeledExpression.baseExpression as? KtLambdaExpression ?: return
            val returnLabel = labeledExpression.getLabelNameAsName() ?: return
            returnSaver.restore(lambdaExpression, returnLabel)
            commentSaver.restore(replaced, forceAdjustIndent = true)
        }
    }

    context(KaSession)
    override fun prepareContext(element: KtNamedFunction): KtExpression? {
        val argument = element.getStrictParentOfType<KtValueArgument>()?.getArgumentExpression()
        val callElement = argument?.getStrictParentOfType<KtCallElement>()
        val typeParameterIndexes = if (callElement != null && callElement.typeArgumentList == null) {
            val functionalType = callElement.resolveToCall()?.singleFunctionCallOrNull()?.argumentMapping?.get(argument)?.symbol?.returnType

            val typeArguments = (functionalType as? KaClassType)?.typeArguments?.let {
                if (it.isNotEmpty()) it.dropLast(1) else it
            }.orEmpty()

            typeArguments.mapIndexedNotNull { index, typeProjection ->
                if (typeProjection.type is KaTypeParameterType) index else null
            }.toSet()
        } else {
            emptySet()
        }


        val returnSaver = ReturnSaver(element)
        val body = element.bodyExpression!!
        return KtPsiFactory(element.project).buildExpression {
            if (!returnSaver.isEmpty) {
                val returnLabels = element.bodyExpression
                    ?.collectDescendantsOfType<KtExpression>()
                    ?.mapNotNull {
                        when (it) {
                            is KtLabeledExpression -> it.getLabelName()
                            is KtCallExpression -> it.calleeExpression?.text
                            else -> null
                        }
                    }
                    .orEmpty()
                val calleeText = callElement?.calleeExpression?.text
                if (callElement == null || calleeText in returnLabels) {
                    val label = KotlinNameSuggester.suggestNameByName(calleeText ?: "block") { it !in returnLabels }
                    appendFixedText("$label@")
                }
            }
            appendFixedText("{")

            val parameters = element.valueParameters

            val needParameters = callElement == null
                    || typeParameterIndexes.isNotEmpty()
                    || parameters.count() > 1
                    || parameters.any { parameter -> ReferencesSearch.search(parameter, LocalSearchScope(body)).any() }

            if (needParameters) {
                parameters.forEachIndexed { index, parameter ->
                    if (index > 0) {
                        appendFixedText(",")
                    }

                    appendName(parameter.nameAsSafeName)
                    val typeReference = parameter.typeReference
                    if (typeReference != null && (callElement == null || index in typeParameterIndexes)) {
                        appendFixedText(": ")
                        appendTypeReference(typeReference)
                    }
                }

                appendFixedText("->")
            }

            if (element.hasBlockBody()) {
                appendChildRange((body as KtBlockExpression).contentRange())
            } else {
                appendExpression(body)
            }

            appendFixedText("}")
        }
    }
}