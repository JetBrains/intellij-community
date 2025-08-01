// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaFlexibleTypeRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

context(KaSession)
fun KaType.isNullableAnyType(): Boolean = isAnyType && isMarkedNullable

context(KaSession)
fun KaType.isNonNullableBooleanType(): Boolean = isBooleanType && !isMarkedNullable

context(KaSession)
fun KaType.isEnum(): Boolean {
    if (this !is KaClassType) return false
    val classSymbol = symbol
    return classSymbol is KaClassSymbol && classSymbol.classKind == KaClassKind.ENUM_CLASS
}

context(KaSession)
fun KtExpression.expressionOrReturnType(): KaType? {
    return if (this is KtDeclaration) returnType else expressionType
}

fun KtExpression.shouldShowType(): Boolean = when (this) {
    is KtFunctionLiteral -> false
    is KtFunction -> !hasBlockBody() && !hasDeclaredReturnType()
    is KtProperty -> typeReference == null
    is KtParameter -> typeReference == null && (isLoopParameter || isLambdaParameter)
    is KtPropertyAccessor -> false
    is KtDestructuringDeclarationEntry -> true
    is KtStatementExpression, is KtDestructuringDeclaration -> false
    is KtIfExpression, is KtWhenExpression, is KtTryExpression -> shouldShowStatementType()
    is KtConstantExpression -> false
    is KtThisExpression -> false
    else -> getQualifiedExpressionForSelector() == null && parent !is KtCallableReferenceExpression && !isFunctionCallee()
}

private fun KtExpression.shouldShowStatementType(): Boolean {
    if (parent !is KtBlockExpression) return true
    if (parent.children.lastOrNull() == this) {
        return analyzeForShowExpressionType(this) { isUsedAsExpression }
    }
    return false
}

private fun KtExpression.isFunctionCallee(): Boolean {
    val callExpression = parent as? KtCallExpression ?: return false
    if (callExpression.calleeExpression != this) return false
    analyzeForShowExpressionType(this) {
        return callExpression.isImplicitInvokeCall() == false
    }
}

// getExpressionsAt is executed from EDT
@OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
private inline fun <R> analyzeForShowExpressionType(
    useSiteElement: KtElement,
    action: KaSession.() -> R
): R = allowAnalysisOnEdt {
    allowAnalysisFromWriteAction {
        analyze(useSiteElement) {
            action()
        }
    }
}

/**
 * Always renders flexible type as its upper bound.
 *
 * TODO should be moved to [KaFlexibleTypeRenderer] and removed from here, see KT-64138
 */
@KaExperimentalApi
object KtFlexibleTypeAsUpperBoundRenderer : KaFlexibleTypeRenderer {
    override fun renderType(
        analysisSession: KaSession,
        type: KaFlexibleType,
        typeRenderer: KaTypeRenderer,
        printer: PrettyPrinter
    ) {
        typeRenderer.renderType(analysisSession, type.upperBound, printer)
    }
}

fun KaType.isInterface(): Boolean {
    if (this !is KaClassType) return false
    val classSymbol = symbol
    return classSymbol is KaClassSymbol && classSymbol.classKind == KaClassKind.INTERFACE
}

fun KaType.containsStarProjections(): Boolean =
    this is KaClassType && typeArguments.any { it is KaStarTypeProjection || it.type?.containsStarProjections() == true }
