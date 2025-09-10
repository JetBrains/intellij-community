// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.Variance

class SuspiciousCallableReferenceInLambdaInspection : KotlinApplicableInspectionBase<KtLambdaExpression, SuspiciousCallableReferenceInLambdaInspection.Context>() {

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

    override fun KaSession.prepareContext(element: KtLambdaExpression): Context? {
        if (!isValidFunctionCallContext(element)) return null
        if (!isValidExpressionUsageContext(element)) return null

        val canMoveResult = canMove(element)
        if (!canMoveResult) {
            return Context(canMove = false, referenceText = null, useNamedArguments = false, lastParameterName = null)
        }

        val callableRefExpr = element.bodyExpression?.statements?.singleOrNull() as? KtCallableReferenceExpression
        val referenceText = if (callableRefExpr != null) {
            buildReferenceText(element, callableRefExpr)
        } else null

        val valueParameters = element.functionLiteral.symbol.valueParameters
        val callExpr = element.getStrictParentOfType<KtCallExpression>()
        val argsBeforeLambda = callExpr?.valueArguments?.filter { it !is KtLambdaArgument } ?: emptyList()
        val useNamedArguments = shouldUseNamedArguments(valueParameters, argsBeforeLambda)
        val lastParameterName = valueParameters.lastOrNull()?.name

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
        val hasDefaults = params.any { it.hasDefaultValue }
        val argsAreNamed = args.any { it.isNamed() }
        return hasDefaults && params.size - 1 > args.size || argsAreNamed
    }

    private fun KaSession.isValidFunctionCallContext(element: KtLambdaExpression): Boolean {
        val functionCall = element.getStrictParentOfType<KtCallExpression>()
            ?.resolveToCall()?.successfulFunctionCallOrNull() ?: return true

        val argumentExpression = (element.parent as? ValueArgument)?.getArgumentExpression()
        val parameter = functionCall.argumentMapping.entries.firstOrNull { it.key == argumentExpression }?.value
        val returnType = (parameter?.returnType as? KaFunctionType)?.returnType

        if (returnType?.isFunctionType == true || returnType?.isSuspendFunctionType == true) return false

        val originalReturnType =
            (functionCall.partiallyAppliedSymbol.symbol.valueParameters[0].returnType as? KaFunctionType)?.returnType ?: return true
        return !originalReturnType.isFunctionInterfaceOrPropertyType() &&
                (originalReturnType !is KaTypeParameterType || originalReturnType.allSupertypes.none { it.isFunctionInterfaceOrPropertyType() })
    }

    private fun KaSession.isValidExpressionUsageContext(element: KtLambdaExpression): Boolean {
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

    @OptIn(KaExperimentalApi::class)
    private fun createQuickFix(context: Context) = object : KotlinModCommandQuickFix<KtLambdaExpression>() {
        override fun getFamilyName() = KotlinBundle.message("move.reference.into.parentheses")

        override fun applyFix(project: Project, element: KtLambdaExpression, updater: ModPsiUpdater) {
            val referenceText = context.referenceText ?: return

            val lambdaArg = element.getStrictParentOfType<KtValueArgument>() as? KtLambdaArgument
            val callExpr = element.getStrictParentOfType<KtCallExpression>() ?: return
            val argsBeforeLambda = callExpr.valueArguments.filter { it !is KtLambdaArgument }

            val newArgList =
                buildNewArgumentList(project, argsBeforeLambda, referenceText, context.useNamedArguments, context.lastParameterName)

            val replacedElement = callExpr.valueArgumentList?.let {
                it.replace(newArgList) as? KtValueArgumentList
            } ?: lambdaArg?.replace(newArgList) as? KtElement

            replacedElement?.let {
                val toShorten = if (it is KtValueArgumentList) it.arguments.lastOrNull() else it
                toShorten?.let { ShortenReferencesFacility.getInstance().shorten(it) }
            }

            if (callExpr.valueArgumentList != null) lambdaArg?.delete()
        }
    }

    private fun buildNewArgumentList(
        project: Project,
        arguments: List<KtValueArgument>,
        referenceText: String,
        useNamedArguments: Boolean,
        lastParameterName: Name?
    ): KtValueArgumentList {
        return KtPsiFactory(project).buildValueArgumentList {
            appendFixedText("(")
            for (arg in arguments) {
                arg.getArgumentName()?.takeIf { useNamedArguments }?.let {
                    appendName(it.asName)
                    appendFixedText(" = ")
                }
                appendExpression(arg.getArgumentExpression())
                appendFixedText(", ")
            }
            if (useNamedArguments && lastParameterName != null) {
                appendName(lastParameterName)
                appendFixedText(" = ")
            }
            appendNonFormattedText(referenceText)
            appendFixedText(")")
        }
    }
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.buildReferenceText(element: KtLambdaExpression, callableRefExpr: KtCallableReferenceExpression): String {
    val callableReference = callableRefExpr.callableReference
    val receiverExpression = callableRefExpr.receiverExpression ?: return "::${callableReference.text.trim()}"

    val receiverSymbol = receiverExpression.mainReference?.resolveToSymbol()
    val lambdaSymbol = element.functionLiteral.symbol

    return if ((receiverSymbol == null || receiverSymbol is KaValueParameterSymbol) && receiverSymbol?.containingSymbol == lambdaSymbol) {
        val callableReferenceCall = callableRefExpr.resolveToCall()?.successfulFunctionCallOrNull()
        val receiverType =
            callableReferenceCall?.partiallyAppliedSymbol?.let { it.extensionReceiver?.type ?: it.dispatchReceiver?.type }
        val typeText = receiverType?.render(position = Variance.INVARIANT)?.substringAfterLast('.') ?: ""
        "$typeText::${callableReference.text.trim()}"
    } else {
        "${receiverExpression.text}::${callableReference.text.trim()}"
    }
}

private fun KaSession.canMove(lambdaExpression: KtLambdaExpression): Boolean {
    val body = lambdaExpression.bodyExpression?.statements?.singleOrNull() as? KtCallableReferenceExpression ?: return false
    val lambdaSymbol = lambdaExpression.functionLiteral.symbol
    val lambdaParam = lambdaSymbol.receiverParameter ?: lambdaSymbol.valueParameters.singleOrNull()
    val lambdaParamType = lambdaParam?.returnType

    // No parameters in lambda and in reference
    if (lambdaParamType == null) {
        val target = body.callableReference.mainReference.resolveToSymbol() ?: return false
        return when (target) {
            is KaVariableSymbol -> (target.returnType as? KaFunctionType)?.parameterTypes?.isEmpty() == true
            is KaFunctionSymbol -> target.valueParameters.isEmpty()
            else -> false
        }
    }

    // Receiver in reference matches parameter
    val receiverSymbol = body.receiverExpression?.mainReference?.resolveToSymbol()
    if (receiverSymbol == lambdaParam) return true

    val receiverType = when (receiverSymbol) {
        is KaVariableSymbol -> receiverSymbol.returnType
        is KaValueParameterSymbol -> receiverSymbol.returnType
        is KaClassSymbol -> receiverSymbol.defaultType
        else -> null
    }

    if (receiverType?.semanticallyEquals(lambdaParamType) == true) return true

    // lambda::invoke — infer from variable's function type
    if (receiverSymbol is KaVariableSymbol) {
        val functionType = receiverSymbol.returnType as? KaFunctionType
        val paramType = functionType?.parameterTypes?.firstOrNull()
        if (paramType?.semanticallyEquals(lambdaParamType) == true) return true
    }

    // Fallback to function resolution
    val funcSymbol = body.callableReference.mainReference.resolveToSymbol() as? KaFunctionSymbol ?: return false
    val paramType = funcSymbol.valueParameters.firstOrNull()?.returnType ?: return false
    return paramType.semanticallyEquals(lambdaParamType)
}

private val functionInterfaces = setOf(
    FqName("kotlin.Function"),
    FqName("kotlin.reflect.KFunction")
)

private val propertyTypes = setOf(
    FqName("kotlin.reflect.KProperty"),
    FqName("kotlin.reflect.KProperty0"),
    FqName("kotlin.reflect.KProperty1"),
    FqName("kotlin.reflect.KMutableProperty"),
    FqName("kotlin.reflect.KMutableProperty0"),
    FqName("kotlin.reflect.KMutableProperty1")
)

private fun KaType.isFunctionInterfaceOrPropertyType(): Boolean {
    val fqName = (this as? KaUsualClassType)?.classId?.asSingleFqName()
    return fqName in functionInterfaces || fqName in propertyTypes
}