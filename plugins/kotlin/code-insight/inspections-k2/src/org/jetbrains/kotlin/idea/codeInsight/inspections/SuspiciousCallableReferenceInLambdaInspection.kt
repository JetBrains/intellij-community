// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.expressions.isUsedAsExpression
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbol
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.defaultType
import org.jetbrains.kotlin.analysis.api.types.isFunctionType
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.types.isSuspendFunctionType
import org.jetbrains.kotlin.analysis.api.types.semanticallyEquals
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenOptionsForIde.Companion.ALL_ENABLED
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.SuspiciousCallableReferenceInLambdaInspection.Context
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithInitializer
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.psi.buildValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolution.KtResolvable
import org.jetbrains.kotlin.types.Variance

class SuspiciousCallableReferenceInLambdaInspection : KotlinApplicableInspectionBase<KtLambdaExpression, Context>() {

    data class Context(
        val canMove: Boolean,
        val referenceText: String?,
        val useNamedArguments: Boolean,
        val lastParameterName: Name?
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitLambdaExpression(expression: KtLambdaExpression) =
                visitTargetElement(expression, holder, isOnTheFly)
        }

    override fun isApplicableByPsi(element: KtLambdaExpression): Boolean =
        element.bodyExpression?.statements?.singleOrNull() is KtCallableReferenceExpression

    context(session: KaSession)
    override fun prepareContext(element: KtLambdaExpression): Context? {
        val callExpr = element.getStrictParentOfType<KtCallExpression>()
        val resolvedCall = callExpr?.resolveSuccessfulCall()

        if (!isValidFunctionCallContext(element, resolvedCall)) return null
        if (!isValidExpressionUsageContext(element)) return null

        val callableRefExpr = element.bodyExpression?.statements?.single() as KtCallableReferenceExpression
        if (!canMove(element, callableRefExpr)) {
            return Context(canMove = false, referenceText = null, useNamedArguments = false, lastParameterName = null)
        }
        val referenceText = buildReferenceText(element, callableRefExpr)

        val calleeParameters = resolvedCall?.symbol?.valueParameters.orEmpty()
        val argsBeforeLambdaInCall = callExpr?.valueArguments?.filter { it !is KtLambdaArgument } ?: emptyList()
        val useNamedArguments = shouldUseNamedArguments(calleeParameters, argsBeforeLambdaInCall)
        val lastParameterName = calleeParameters.lastOrNull()?.name

        return Context(
            canMove = true,
            referenceText = referenceText,
            useNamedArguments = useNamedArguments,
            lastParameterName = lastParameterName
        )
    }

    private fun shouldUseNamedArguments(
        params: List<KaValueParameterSymbol>,
        args: List<KtValueArgument>
    ): Boolean {
        val hasDefaults = params.any { it.hasDeclaredDefaultValue }
        val argsAreNamed = args.any { it.isNamed() }
        return argsAreNamed || (hasDefaults && params.size - 1 > args.size)
    }

    context(session: KaSession)
    private fun isValidFunctionCallContext(element: KtLambdaExpression, functionCall: KaFunctionCall<*>?): Boolean {
        if (functionCall == null) return true

        val argumentExpression = (element.parent as? ValueArgument)?.getArgumentExpression()
        val parameter = functionCall.valueArgumentMapping[argumentExpression] ?: return true
        val returnType = (parameter.returnType as? KaFunctionType)?.returnType

        if (returnType?.isFunctionType == true || returnType?.isSuspendFunctionType == true) return false

        val originalReturnType = (parameter.symbol.returnType as? KaFunctionType)?.returnType ?: return true
        return !originalReturnType.isSubtypeOf(StandardClassIds.Function) &&
                !originalReturnType.isSubtypeOf(StandardClassIds.KProperty)
    }

    context(session: KaSession)
    private fun isValidExpressionUsageContext(element: KtLambdaExpression): Boolean {
        val callElement = element.getStrictParentOfType<KtCallExpression>() as? KtExpression ?: element
        if (!callElement.isUsedAsExpression) return true

        val qualifiedOrThis = callElement.getQualifiedExpressionForSelectorOrThis()
        val parentDeclaration = qualifiedOrThis.getStrictParentOfType<KtDeclaration>()
        val initializer = (parentDeclaration as? KtDeclarationWithInitializer)?.initializer
        val typeReference = (parentDeclaration as? KtCallableDeclaration)?.typeReference

        return qualifiedOrThis == initializer && typeReference == null
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtLambdaExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        val description = KotlinBundle.message("suspicious.callable.reference.as.the.only.lambda.element")
        val highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING

        return if (context.canMove && context.referenceText != null) {
            createProblemDescriptor(element, rangeInElement, description, highlightType, onTheFly, createQuickFix(context))
        } else {
            createProblemDescriptor(element, rangeInElement, description, highlightType, onTheFly)
        }
    }

    private fun createQuickFix(context: Context) = object : KotlinModCommandQuickFix<KtLambdaExpression>() {
        override fun getFamilyName() = KotlinBundle.message("move.reference.into.parentheses")

        override fun applyFix(project: Project, element: KtLambdaExpression, updater: ModPsiUpdater) {
            val referenceText = context.referenceText ?: return
            val lambdaArg = element.getStrictParentOfType<KtValueArgument>() as? KtLambdaArgument

            // Inline lambda (not a trailing lambda)
            if (lambdaArg == null) {
                val referenceExpr = KtPsiFactory(project).createExpression(referenceText)
                val replaced = element.replace(referenceExpr) as? KtElement
                replaced?.let { shortenReferences(it, shortenOptions = ALL_ENABLED) }
                return
            }

            // trailing lambda
            val callExpr = element.getStrictParentOfType<KtCallExpression>() ?: return
            val argsBeforeLambda = callExpr.valueArguments.filter { it !is KtLambdaArgument }

            val newArgList = buildNewArgumentList(project, argsBeforeLambda, referenceText, context)

            val valueArgumentList = callExpr.valueArgumentList
            val replacedElement = valueArgumentList?.let {
                it.replace(newArgList) as? KtValueArgumentList
            } ?: lambdaArg.replace(newArgList) as? KtElement

            replacedElement?.let { element ->
                val toShorten = if (element is KtValueArgumentList) element.arguments.lastOrNull() else element
                toShorten?.let { shortenReferences(it, shortenOptions = ALL_ENABLED) }
            }

            if (valueArgumentList != null) lambdaArg.delete()
        }
    }
}

private fun buildNewArgumentList(
    project: Project,
    arguments: List<KtValueArgument>,
    referenceText: String,
    context: Context
): KtValueArgumentList {
    return KtPsiFactory(project).buildValueArgumentList {
        appendFixedText("(")
        for (arg in arguments) {
            arg.getArgumentName()?.takeIf { context.useNamedArguments }?.let {
                appendName(it.asName)
                appendFixedText(" = ")
            }
            appendExpression(arg.getArgumentExpression())
            appendFixedText(", ")
        }
        if (context.useNamedArguments && context.lastParameterName != null) {
            appendName(context.lastParameterName)
            appendFixedText(" = ")
        }
        appendNonFormattedText(referenceText)
        appendFixedText(")")
    }
}

context(session: KaSession)
private fun buildReferenceText(element: KtLambdaExpression, callableRefExpr: KtCallableReferenceExpression): String {
    val callableRefText = callableRefExpr.callableReference.text.trim()
    val receiverExpression = callableRefExpr.receiverExpression ?: return "::$callableRefText"

    val receiverSymbol = (receiverExpression as? KtResolvable)?.resolveSuccessfulSymbol()
    val lambdaSymbol = element.functionLiteral.symbol

    val receiverText = if (receiverSymbol is KaValueParameterSymbol && receiverSymbol.containingSymbol == lambdaSymbol) {
        val callableReferenceCall = callableRefExpr.resolveSuccessfulCall()
        val receiverType = callableReferenceCall?.let { it.extensionReceiver?.type ?: it.dispatchReceiver?.type }
        receiverType?.render(position = Variance.INVARIANT) ?: ""
    } else {
        receiverExpression.text
    }
    return "$receiverText::$callableRefText"
}

context(session: KaSession)
private fun canMove(lambdaExpression: KtLambdaExpression, callableRefExpr: KtCallableReferenceExpression): Boolean {
    val lambdaSymbol = lambdaExpression.functionLiteral.symbol
    val lambdaParam = lambdaSymbol.receiverParameter ?: lambdaSymbol.valueParameters.singleOrNull()
    val lambdaParamType = lambdaParam?.returnType

    val target = callableRefExpr.resolveSuccessfulSymbol()

    // No parameters in lambda and in reference
    if (lambdaParamType == null) {
        return when (target) {
            is KaVariableSymbol -> (target.returnType as? KaFunctionType)?.parameterTypes?.isEmpty() == true
            is KaFunctionSymbol -> target.valueParameters.isEmpty()
            else -> false
        }
    }

    // Receiver in reference matches parameter
    val receiverSymbol = (callableRefExpr.receiverExpression as? KtResolvable)?.resolveSuccessfulSymbol()
    if (receiverSymbol == lambdaParam) return true

    val receiverType = when (receiverSymbol) {
        is KaClassSymbol -> receiverSymbol.defaultType
        is KaVariableSymbol -> if (target == null) receiverSymbol.returnType else null
        else -> null
    }

    if (receiverType?.semanticallyEquals(lambdaParamType) == true) return true

    // lambda::invoke — infer from variable's function type
    if (receiverSymbol is KaVariableSymbol) {
        val singleParamType = (receiverSymbol.returnType as? KaFunctionType)?.parameterTypes?.singleOrNull()
        if (singleParamType?.semanticallyEquals(lambdaParamType) == true) return true
    }

    // Fallback to function resolution
    val funcSymbol = target as? KaFunctionSymbol ?: return false
    val singleParam = funcSymbol.valueParameters.singleOrNull() ?: return false
    return singleParam.returnType.semanticallyEquals(lambdaParamType)
}
