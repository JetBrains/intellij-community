// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSamConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNonPublicApi
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiMutationService
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.types.Variance

data class SamConversionToAnonymousObjectContext(
    val interfaceName: String,
    val typeArgumentsText: String,
    val functionText: String,
)

fun KtCallExpression.getLambdaExpressionForSamConversion(): KtLambdaExpression? =
    lambdaArguments.firstOrNull()?.getLambdaExpression()
        ?: valueArguments.firstOrNull()?.getArgumentExpression() as? KtLambdaExpression

context(_: KaSession)
fun KtFunctionLiteral.hasRecursiveSamCall(
    samName: String,
    lambdaParameters: List<KaValueParameterSymbol>,
): Boolean {
    return anyDescendantOfType<KtCallExpression> { nestedCall ->
        if (nestedCall.calleeExpression?.text != samName) return@anyDescendantOfType false
        val callArguments = nestedCall.valueArguments
        if (callArguments.size != lambdaParameters.size) return@anyDescendantOfType false

        callArguments.zip(lambdaParameters).all { (argument, parameter) ->
            val argumentType = argument.getArgumentExpression()?.expressionType ?: return@all false
            argumentType.isSubtypeOf(parameter.returnType)
        }
    }
}

/**
 * Returns `true` if the callee resolves to a type alias whose expanded
 * type contains a variance projection (`in`, `out`, or `*`).
 *
 * Converting such a SAM lambda to an anonymous object is invalid: the projected
 * type cannot legally appear as a supertype, whether through the alias or with it
 * inlined. The SAM constructor call form is accepted by the compiler even when the
 * alias expands to a projected type, but neither `object : IInA<Int>` nor
 * `object : I<in Int>` compiles.
 */
context(_: KaSession)
fun KtExpression.isSamConversionAliasedWithVariance(): Boolean {
    val resolvedSymbol = mainReference?.resolveToSymbol() as? KaSamConstructorSymbol ?: return false
    return resolvedSymbol.returnType
        .takeIf { it.abbreviation?.symbol is KaTypeAliasSymbol }
        ?.hasVariance()
        ?: false
}

@OptIn(KtNonPublicApi::class)
fun applySamConversionToAnonymousObject(
    element: KtCallExpression,
    conversionContext: SamConversionToAnonymousObjectContext,
) {
    val psiFactory = KtPsiFactory(element.project)

    val stubFunction = psiFactory.createFunction(conversionContext.functionText)
    KtPsiMutationService.getInstance().addModifierKeyword(stubFunction, KtTokens.OVERRIDE_KEYWORD)

    val parentOfCall = element.getQualifiedExpressionForSelector() ?: element
    val objectLiteral = psiFactory.createExpression(
        "${KtTokens.OBJECT_KEYWORD} : ${conversionContext.interfaceName}${conversionContext.typeArgumentsText} { ${stubFunction.text} }"
    )
    val replaced = parentOfCall.replaced(objectLiteral)

    shortenReferences(replaced)
    replaced.reformat(canChangeWhiteSpacesOnly = true)
}

private fun KaType.hasVariance(): Boolean =
    (this as? KaClassType)
        ?.typeArguments
        ?.filterIsInstance<KaTypeArgumentWithVariance>()
        ?.any { it.variance != Variance.INVARIANT || it.type.hasVariance() }
        ?: false
