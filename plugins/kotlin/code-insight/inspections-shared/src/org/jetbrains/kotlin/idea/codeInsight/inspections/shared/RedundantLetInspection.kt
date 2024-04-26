// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.parents

private val KOTLIN_LET_FQ_NAME: FqName = StandardNames.BUILT_INS_PACKAGE_FQ_NAME + "let"

internal sealed class RedundantLetInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, Unit>() {

    final override fun getProblemDescription(
        element: KtCallExpression,
        context: Unit,
    ): String = KotlinBundle.message("redundant.let.call.could.be.removed")

    final override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRanges.calleeExpression(element)

    context(KtAnalysisSession)
    override fun prepareContext(element: KtCallExpression): Unit? {
        if (!element.isCalling(sequenceOf(KOTLIN_LET_FQ_NAME))) return null
        val lambdaExpression = element.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return null
        val parameterName = lambdaExpression.getParameterName() ?: return null
        val bodyExpression = lambdaExpression.bodyExpression?.children?.singleOrNull() ?: return null

        return isApplicable(
            element,
            bodyExpression,
            lambdaExpression,
            parameterName,
        ).asUnit
    }

    context(KtAnalysisSession)
    protected abstract fun isApplicable(
        element: KtCallExpression,
        bodyExpression: PsiElement,
        lambdaExpression: KtLambdaExpression,
        parameterName: String,
    ): Boolean

    final override fun createQuickFix(
        element: KtCallExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("remove.let.call")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            val lambdaExpression = element.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return

            val bodyExpression = lambdaExpression.bodyExpression?.children?.singleOrNull() ?: return

            when (bodyExpression) {
                is KtDotQualifiedExpression -> bodyExpression.applyTo(element)
                is KtBinaryExpression -> bodyExpression.applyTo(element)
                is KtCallExpression -> bodyExpression.applyTo(element, lambdaExpression.functionLiteral, updater)
                is KtSimpleNameExpression -> deleteCall(element)
            }
        }
    }

    final override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }
}

internal class SimpleRedundantLetInspection : RedundantLetInspection() {

    context(KtAnalysisSession)
    override fun isApplicable(
        element: KtCallExpression,
        bodyExpression: PsiElement,
        lambdaExpression: KtLambdaExpression,
        parameterName: String,
    ): Boolean = when (bodyExpression) {
        is KtDotQualifiedExpression -> bodyExpression.isApplicable(parameterName)
        is KtSimpleNameExpression -> bodyExpression.text == parameterName
        else -> false
    }
}

internal class ComplexRedundantLetInspection : RedundantLetInspection() {

    context(KtAnalysisSession)
    override fun isApplicable(
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

    override fun getProblemHighlightType(
        element: KtCallExpression,
        context: Unit,
    ): ProblemHighlightType =
        if (isSingleLine(element)) ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        else ProblemHighlightType.INFORMATION
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

private fun KtCallExpression.applyTo(
    element: KtCallExpression,
    functionLiteral: KtFunctionLiteral,
    updater: ModPsiUpdater,
) {
    val parent = element.parent as? KtQualifiedExpression
    val reference = analyze(functionLiteral) {
        functionLiteral.valueParameterReferences(callExpression = this@applyTo)
    }.firstOrNull()

    val replaced = if (parent != null) {
        reference?.replace(parent.receiverExpression)
        parent.replaced(this)
    } else {
        reference?.replace(KtPsiFactory(project).createThisExpression())
        element.replaced(this)
    }

    updater.moveCaretTo(replaced)
}

context(KtAnalysisSession)
private fun KtBinaryExpression.isApplicable(parameterName: String, isTopLevel: Boolean = true): Boolean {
    val left = left ?: return false
    if (isTopLevel) {
        when (left) {
            is KtNameReferenceExpression -> if (left.text != parameterName) return false
            is KtDotQualifiedExpression -> if (!left.isApplicable(parameterName)) return false
            else -> return false
        }
    } else {
        if (!left.isApplicable(parameterName)) return false
    }

    val right = right ?: return false
    return right.isApplicable(parameterName)
}

context(KtAnalysisSession)
private fun KtExpression.isApplicable(parameterName: String): Boolean = when (this) {
    is KtNameReferenceExpression -> text != parameterName
    is KtDotQualifiedExpression -> !hasLambdaExpression() && !nameUsed(parameterName)
    is KtBinaryExpression -> isApplicable(parameterName, isTopLevel = false)
    is KtCallExpression -> isApplicable(parameterName)
    is KtConstantExpression -> true
    else -> false
}

context(KtAnalysisSession)
private fun KtCallExpression.isApplicable(parameterName: String): Boolean {
    if (valueArguments.isEmpty()) return false
    return valueArguments.all {
        val argumentExpression = it.getArgumentExpression() ?: return@all false
        argumentExpression.isApplicable(parameterName)
    }
}

context(KtAnalysisSession)
private fun KtDotQualifiedExpression.isApplicable(parameterName: String): Boolean {
    return !hasLambdaExpression()
            && getLeftMostReceiverExpression().let { receiver ->
        receiver is KtNameReferenceExpression
                && receiver.getReferencedName() == parameterName
                && !nameUsed(parameterName, except = receiver)
    } && callExpression?.resolveCall()?.let {
        it.successfulFunctionCallOrNull() == null
                && it.successfulVariableAccessCall() != null
    } != true
            && !getHasNullableReceiverExtensionCall()
}

context(KtAnalysisSession)
private fun KtDotQualifiedExpression.getHasNullableReceiverExtensionCall(): Boolean {
    val hasNullableType = selectorExpression
        ?.resolveCall()
        ?.successfulFunctionCallOrNull()
        ?.partiallyAppliedSymbol
        ?.extensionReceiver
        ?.type
        ?.isNullableAnyType()
    if (hasNullableType == true) return true
    return (KtPsiUtil.deparenthesize(receiverExpression) as? KtDotQualifiedExpression)?.getHasNullableReceiverExtensionCall() == true
}

private fun KtDotQualifiedExpression.hasLambdaExpression(): Boolean =
    selectorExpression?.anyDescendantOfType<KtLambdaExpression>() ?: false

private fun KtLambdaExpression.getParameterName(): String? {
    val parameters = valueParameters
    if (parameters.size > 1) return null
    return if (parameters.size == 1) parameters[0].text else StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
}

private fun KtExpression.nameUsed(name: String, except: KtNameReferenceExpression? = null): Boolean =
    anyDescendantOfType<KtNameReferenceExpression> { it != except && it.getReferencedName() == name }

context(KtAnalysisSession)
private fun KtFunctionLiteral.valueParameterReferences(callExpression: KtCallExpression): List<KtNameReferenceExpression> {
    val valueParameterSymbol = getAnonymousFunctionSymbol().valueParameters
        .singleOrNull()
        ?: return emptyList()

    val variableSymbolByName: Map<Name, KtSymbol> = valueParameters.singleOrNull()
        ?.destructuringDeclaration
        ?.entries
        ?.asSequence()
        ?.map { it.getSymbol() }
        ?.filterIsInstance<KtNamedSymbol>()
        ?.associateBy { it.name }
        ?: mapOf(valueParameterSymbol.name to valueParameterSymbol)

    val callee = callExpression.calleeExpression as? KtNameReferenceExpression
        ?: return emptyList()

    val arguments = variableSymbolByName[callee.getReferencedNameAsName()]?.takeIf {
        it == callee.mainReference.resolveToSymbols().singleOrNull()
    }?.let { listOf(callee) }
        ?: emptyList()

    return arguments + callExpression.valueArguments.flatMap { valueArgument ->
        valueArgument.collectDescendantsOfType<KtNameReferenceExpression>().filter { referenceExpression ->
            variableSymbolByName[referenceExpression.getReferencedNameAsName()]?.takeIf {
                it == referenceExpression.resolveCall()?.successfulVariableAccessCall()?.symbol
            } != null
        }
    }
}

private fun isSingleLine(element: KtCallExpression): Boolean {
    val qualifiedExpression = element.getQualifiedExpressionForSelector() ?: return true
    var receiver = qualifiedExpression.receiverExpression
    if (receiver.isMultiLine()) return false
    var count = 1
    while (true) {
        if (count > 2) return false
        receiver = (receiver as? KtQualifiedExpression)?.receiverExpression ?: break
        count++
    }
    return true
}

private fun KtDotQualifiedExpression.deleteFirstReceiver(): KtExpression {
    when (val receiver = receiverExpression) {
        is KtDotQualifiedExpression -> receiver.deleteFirstReceiver()
        else -> selectorExpression?.let { replace(it) as KtExpression }
            ?.let { return it }
    }
    return this
}