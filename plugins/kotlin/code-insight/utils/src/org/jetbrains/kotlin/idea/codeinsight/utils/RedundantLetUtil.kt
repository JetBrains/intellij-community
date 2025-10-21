// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbols
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.parents

@ApiStatus.Internal
context(_: KaSession)
fun isLetCallRedundant(element: KtCallExpression): Boolean {
    if (!element.isCallingAnyOf(StandardKotlinNames.let)) return false
    val lambdaExpression = element.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return false
    val parameterName = lambdaExpression.getParameterName() ?: return false
    val bodyExpression = lambdaExpression.bodyExpression?.children?.singleOrNull() ?: return false

    return isLetCallRedundant(element, bodyExpression, lambdaExpression, parameterName)
}

@ApiStatus.Internal
context(_: KaSession)
fun isLetCallRedundant(
    element: KtCallExpression,
    bodyExpression: PsiElement,
    lambdaExpression: KtLambdaExpression,
    parameterName: String,
): Boolean {
    if (bodyExpression is KtBinaryExpression) {
        return element.parent !is KtSafeQualifiedExpression && bodyExpression.isApplicable(parameterName)
    }

    if (bodyExpression !is KtCallExpression) return false
    if (element.parent is KtSafeQualifiedExpression) return false

    val functionLiteral = lambdaExpression.functionLiteral
    val parameterReferences = functionLiteral.valueParameterReferences(bodyExpression)
    if (parameterReferences.isEmpty()) {
        val receiver = element.getQualifiedExpressionForSelector()?.receiverExpression
        val receiverIsWithoutSideEffects = receiver?.anyDescendantOfType<KtCallElement>() != true
        return receiverIsWithoutSideEffects
    }

    val destructuringDeclaration = functionLiteral.valueParameters.firstOrNull()?.destructuringDeclaration
    if (destructuringDeclaration != null) return false

    val singleReferenceNotInsideInnerLambda = parameterReferences.singleOrNull()?.takeIf { reference ->
        reference.parents.takeWhile { it != functionLiteral }.none { it is KtFunction }
    }
    return singleReferenceNotInsideInnerLambda != null
}

@OptIn(KaContextParameterApi::class)
@ApiStatus.Internal
context(_: KaSession)
fun KtDotQualifiedExpression.isLetCallRedundant(parameterName: String): Boolean {
    return !hasLambdaExpression() && getLeftMostReceiverExpression().let { receiver ->
        receiver is KtNameReferenceExpression && receiver.getReferencedName() == parameterName && !nameUsed(
            parameterName,
            except = receiver
        )
    } && callExpression?.resolveToCall()?.let {
        it.successfulFunctionCallOrNull() == null && it.successfulVariableAccessCall() != null
    } != true && !getHasNullableReceiverExtensionCall()
}

fun removeRedundantLetCall(element: KtCallExpression, updateCaret: (KtElement) -> Unit) {
    val lambdaExpression = element.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return

    val bodyExpression = lambdaExpression.bodyExpression?.children?.singleOrNull() ?: return

    when (bodyExpression) {
        is KtDotQualifiedExpression -> bodyExpression.applyTo(element)
        is KtBinaryExpression -> bodyExpression.applyTo(element)
        is KtCallExpression -> bodyExpression.applyTo(element, lambdaExpression.functionLiteral, updateCaret)
        is KtSimpleNameExpression -> deleteCall(element)
    }
}

private fun KtLambdaExpression.getParameterName(): String? {
    val parameters = valueParameters
    if (parameters.size > 1) return null
    return if (parameters.size == 1) parameters[0].text else StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
}

private fun KtBinaryExpression.applyTo(element: KtCallExpression) {
    val left = left ?: return
    val factory = KtPsiFactory(element.project)
    when (val parent = element.parent) {
        is KtQualifiedExpression -> {
            val receiver = parent.receiverExpression
            val newLeft = when (left) {
                is KtDotQualifiedExpression -> left.replaceFirstReceiver(factory, receiver, parent is KtSafeQualifiedExpression)
                else -> receiver
            }
            val newExpression = factory.createExpressionByPattern("$0 $1 $2", newLeft, operationReference, right!!)
            parent.replace(newExpression)
        }

        else -> {
            val newLeft = when (left) {
                is KtDotQualifiedExpression -> left.deleteFirstReceiver()
                else -> factory.createThisExpression()
            }
            val newExpression = factory.createExpressionByPattern("$0 $1 $2", newLeft, operationReference, right!!)
            element.replace(newExpression)
        }
    }
}

private fun KtDotQualifiedExpression.applyTo(element: KtCallExpression) {
    when (val parent = element.parent) {
        is KtQualifiedExpression -> {
            val factory = KtPsiFactory(element.project)
            val receiver = parent.receiverExpression
            parent.replace(replaceFirstReceiver(factory, receiver, parent is KtSafeQualifiedExpression))
        }

        else -> {
            element.replace(deleteFirstReceiver())
        }
    }
}

private fun deleteCall(element: KtCallExpression) {
    val parent = element.parent as? KtQualifiedExpression
    if (parent != null) {
        val replacement = parent.selectorExpression?.takeIf { it != element } ?: parent.receiverExpression
        parent.replace(replacement)
    } else {
        element.delete()
    }
}

@OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
private fun KtCallExpression.applyTo(
    element: KtCallExpression,
    functionLiteral: KtFunctionLiteral,
    updateCaret: (KtElement) -> Unit,
) {
    val parent = element.parent as? KtQualifiedExpression
    val reference = allowAnalysisFromWriteAction {
        allowAnalysisOnEdt {
            analyze(functionLiteral) {
                functionLiteral.valueParameterReferences(callExpression = this@applyTo)
            }
        }
    }.firstOrNull()

    val replaced = if (parent != null) {
        reference?.replace(parent.receiverExpression)
        parent.replaced(this)
    } else {
        reference?.replace(KtPsiFactory(project).createThisExpression())
        element.replaced(this)
    }

    updateCaret(replaced)
}

private fun KtDotQualifiedExpression.deleteFirstReceiver(): KtExpression {
    when (val receiver = receiverExpression) {
        is KtDotQualifiedExpression -> receiver.deleteFirstReceiver()
        else -> selectorExpression?.let { replace(it) as KtExpression }?.let { return it }
    }
    return this
}

@OptIn(KaContextParameterApi::class)
context(_: KaSession)
private fun KtFunctionLiteral.valueParameterReferences(callExpression: KtCallExpression): List<KtNameReferenceExpression> {
    val valueParameterSymbol = symbol.valueParameters.singleOrNull() ?: return emptyList()

    val variableSymbolByName: Map<Name, KaSymbol> =
        valueParameters.singleOrNull()?.destructuringDeclaration?.entries?.asSequence()?.map { it.symbol }
            ?.filterIsInstance<KaNamedSymbol>()?.associateBy { it.name } ?: mapOf(valueParameterSymbol.name to valueParameterSymbol)

    val callee = callExpression.calleeExpression as? KtNameReferenceExpression ?: return emptyList()

    val arguments = variableSymbolByName[callee.getReferencedNameAsName()]?.takeIf {
        it == callee.mainReference.resolveToSymbols().singleOrNull()
    }?.let { listOf(callee) } ?: emptyList()

    return arguments + callExpression.valueArguments.flatMap { valueArgument ->
        valueArgument.collectDescendantsOfType<KtNameReferenceExpression>().filter { referenceExpression ->
            variableSymbolByName[referenceExpression.getReferencedNameAsName()]?.takeIf {
                it == referenceExpression.resolveToCall()?.successfulVariableAccessCall()?.symbol
            } != null
        }
    }
}

context(_: KaSession)
private fun KtBinaryExpression.isApplicable(parameterName: String, isTopLevel: Boolean = true): Boolean {
    val left = left ?: return false
    if (isTopLevel) {
        when (left) {
            is KtNameReferenceExpression -> if (left.text != parameterName) return false
            is KtDotQualifiedExpression -> if (!left.isLetCallRedundant(parameterName)) return false
            else -> return false
        }
    } else {
        if (!left.isApplicable(parameterName)) return false
    }

    val right = right ?: return false
    return right.isApplicable(parameterName)
}

context(_: KaSession)
private fun KtExpression.isApplicable(parameterName: String): Boolean = when (this) {
    is KtNameReferenceExpression -> text != parameterName
    is KtDotQualifiedExpression -> !hasLambdaExpression() && !nameUsed(parameterName)
    is KtBinaryExpression -> isApplicable(parameterName, isTopLevel = false)
    is KtCallExpression -> isApplicable(parameterName)
    is KtConstantExpression -> true
    else -> false
}

context(_: KaSession)
private fun KtCallExpression.isApplicable(parameterName: String): Boolean {
    if (valueArguments.isEmpty()) return false
    return valueArguments.all {
        val argumentExpression = it.getArgumentExpression() ?: return@all false
        argumentExpression.isApplicable(parameterName)
    }
}


private fun KtExpression.nameUsed(name: String, except: KtNameReferenceExpression? = null): Boolean =
    anyDescendantOfType<KtNameReferenceExpression> { it != except && it.getReferencedName() == name }


@OptIn(KaContextParameterApi::class)
context(_: KaSession)
private fun KtDotQualifiedExpression.getHasNullableReceiverExtensionCall(): Boolean {
    val hasNullableType = selectorExpression?.resolveToCall()
        ?.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.extensionReceiver?.type?.isNullableAnyType()
    if (hasNullableType == true) return true
    return (KtPsiUtil.deparenthesize(receiverExpression) as? KtDotQualifiedExpression)?.getHasNullableReceiverExtensionCall() == true
}

private fun KtDotQualifiedExpression.hasLambdaExpression(): Boolean = selectorExpression?.anyDescendantOfType<KtLambdaExpression>() ?: false