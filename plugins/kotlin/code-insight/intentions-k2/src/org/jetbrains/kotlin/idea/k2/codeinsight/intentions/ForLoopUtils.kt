// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.components.targetSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.idea.codeinsight.utils.dereferenceValidPointers
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

@ApiStatus.Internal
object ForLoopUtils {
    internal typealias ReturnsToReplace = List<SmartPsiElementPointer<KtReturnExpression>>

    context(_: KaSession)
    internal fun KtLambdaExpression.computeReturnsToReplace(): ReturnsToReplace {
        val lambdaBody = bodyExpression ?: return emptyList()
        val functionLiteralSymbol = functionLiteral.symbol
        return buildList {
            lambdaBody.forEachDescendantOfType<KtReturnExpression> { returnExpression ->
                if (returnExpression.targetSymbol == functionLiteralSymbol) {
                    add(returnExpression.createSmartPointer())
                }
            }
        }
    }

    internal fun suggestLoopName(lambda: KtLambdaExpression): String =
        KotlinNameSuggester.suggestNameByName("loop") { candidate ->
            val b = !lambda.anyDescendantOfType<KtLabeledExpression> { it.getLabelName() == candidate }
            b
        }

    context(_: KaSession)
    internal fun suggestLoopVariableName(lambda: KtLambdaExpression, factory: KtPsiFactory): KtParameter {
        val body = lambda.bodyExpression
        val validator: (String) -> Boolean = { candidate ->
            if (body == null) {
                lambda.valueParameters.none { it.name == candidate }
            } else {
                // Check if the candidate would conflict with existing names
                !body.anyDescendantOfType<KtSimpleNameExpression> { nameExpr ->
                    nameExpr.getReferencedName() == candidate && nameExpr.mainReference.resolveToSymbol() != null
                }
            }
        }
        val suggestedName = KotlinNameSuggester.suggestNameByName("i", validator)
        return factory.createLoopParameter(suggestedName)
    }

    context(_: KaSession)
    internal fun replaceImplicitItReferences(lambda: KtLambdaExpression, newParameter: KtParameter, factory: KtPsiFactory) {
        val body = lambda.bodyExpression ?: return
        val newName = newParameter.name ?: return
        val explicitParam = lambda.valueParameters.singleOrNull()

        if (explicitParam != null && explicitParam.name != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier) return

        val parameterSymbol = explicitParam?.symbol ?: lambda.functionLiteral.symbol.valueParameters.singleOrNull() ?: return

        body.forEachDescendantOfType<KtNameReferenceExpression> { reference ->
            if (reference.getReferencedName() == StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier &&
                reference.mainReference.resolveToSymbol() == parameterSymbol) {
                reference.replace(factory.createExpression(newName))
            }
        }
    }

    internal fun replaceReturnsWithContinue(
        returnsToReplace: ReturnsToReplace,
        lambda: KtLambdaExpression,
        loopLabelName: String,
        factory: KtPsiFactory
    ): Boolean {
        var needLoopLabel = false
        for (returnExpr in returnsToReplace.dereferenceValidPointers()) {
            val immediateParentLambda = returnExpr.getStrictParentOfType<KtLambdaExpression>()
            val parentLoop = returnExpr.getStrictParentOfType<KtLoopExpression>()

            val isInsideNestedLambda = immediateParentLambda != lambda
            val isInsideLoopInLambda = parentLoop != null && parentLoop.getStrictParentOfType<KtLambdaExpression>() == lambda

            if (isInsideNestedLambda || isInsideLoopInLambda) {
                returnExpr.replace(factory.createExpression("continue@$loopLabelName"))
                needLoopLabel = true
            } else {
                returnExpr.replace(factory.createExpression("continue"))
            }
        }
        return needLoopLabel
    }
}