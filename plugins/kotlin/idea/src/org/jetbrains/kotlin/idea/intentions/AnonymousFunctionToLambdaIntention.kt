// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.KotlinNameSuggester
import org.jetbrains.kotlin.idea.core.getLastLambdaExpression
import org.jetbrains.kotlin.idea.core.moveFunctionLiteralOutsideParenthesesIfPossible
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.contentRange
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.resolve.calls.util.getParameterForArgument
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

class AnonymousFunctionToLambdaIntention : SelfTargetingRangeIntention<KtNamedFunction>(
    KtNamedFunction::class.java,
    KotlinBundle.lazyMessage("convert.to.lambda.expression"),
    KotlinBundle.lazyMessage("convert.anonymous.function.to.lambda.expression")
) {
    override fun applicabilityRange(element: KtNamedFunction): TextRange? {
        if (element.name != null || !element.hasBody()) return null
        return element.funKeyword?.textRange
    }

    override fun applyTo(element: KtNamedFunction, editor: Editor?) {
        val argument = element.getStrictParentOfType<KtValueArgument>()
        val callElement = argument?.getStrictParentOfType<KtCallElement>()
        val typeParameterIndexes = if (callElement != null && callElement.typeArgumentList == null) {
            val functionalType = callElement.resolveToCall()?.getParameterForArgument(argument)?.let {
                if (it.isVararg) it.original.type.arguments.firstOrNull()?.type else it.original.type
            }

            val typeArguments = functionalType?.arguments?.let {
                if (it.isNotEmpty()) it.dropLast(1) else it
            }.orEmpty()

            typeArguments.mapIndexedNotNull { index, typeProjection ->
                if (typeProjection.type.isTypeParameter()) index else null
            }.toSet()
        } else {
            emptySet()
        }

        val commentSaver = CommentSaver(element)
        val returnSaver = ReturnSaver(element)
        val body = element.bodyExpression!!
        val newExpression = KtPsiFactory(element).buildExpression {
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

        val replaced = element.replaced(newExpression)
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
}