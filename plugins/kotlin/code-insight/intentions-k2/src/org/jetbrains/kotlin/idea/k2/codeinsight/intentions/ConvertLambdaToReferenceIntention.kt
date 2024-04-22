// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertLambdaToReferenceUtils.getCallReferencedName
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertLambdaToReferenceUtils.getSafeReferencedName
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertLambdaToReferenceUtils.isArgument
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertLambdaToReferenceUtils.singleStatementOrNull
import org.jetbrains.kotlin.idea.codeinsight.utils.addTypeArguments
import org.jetbrains.kotlin.idea.codeinsight.utils.getRenderedTypeArguments
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.Variance

internal class ConvertLambdaToReferenceIntention :
    KotlinApplicableModCommandAction<KtLambdaExpression, ConvertLambdaToReferenceIntention.Context>(KtLambdaExpression::class) {

    data class Context(
        val newElement: SmartPsiElementPointer<KtElement>,
        val renderedPropertyType: String?,
        val renderedTypeArguments: String?,
    )

    override fun getFamilyName(): String = KotlinBundle.message("convert.lambda.to.reference.before.text")

    override fun getActionName(
      actionContext: ActionContext,
      element: KtLambdaExpression,
      elementContext: Context,
    ): String = KotlinBundle.message("convert.lambda.to.reference")

    override fun isApplicableByPsi(element: KtLambdaExpression): Boolean {
        val singleStatement = element.singleStatementOrNull() ?: return false

        return when (singleStatement) {
            is KtCallExpression -> getCalleeReferenceExpression(callableExpression = singleStatement) != null
            is KtNameReferenceExpression -> false
            is KtDotQualifiedExpression -> {
                val selector = singleStatement.selectorExpression ?: return false
                if (singleStatement.receiverExpression is KtSuperExpression) return false
                getCalleeReferenceExpression(callableExpression = selector) != null
            }

            else -> false
        }
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtLambdaExpression): Context? {
        val singleStatement = element.singleStatementOrNull() ?: return null
        when (singleStatement) {
            is KtCallExpression -> {
                if (!isConvertibleCallInLambdaByAnalyze(callableExpression = singleStatement, lambdaExpression = element)) return null
            }

            is KtDotQualifiedExpression -> {
                val selector = singleStatement.selectorExpression ?: return null
                if (!isConvertibleCallInLambdaByAnalyze(
                        callableExpression = selector,
                        explicitReceiver = singleStatement.receiverExpression,
                        lambdaExpression = element
                    )
                ) return null
            }

            else -> return null
        }
        val referenceName = buildReferenceText(element) ?: return null
        val psiFactory = KtPsiFactory(element.project)
        val parent = element.parent

        val outerCallExpression = parent.getStrictParentOfType<KtCallExpression>()
        val resolvedOuterCall = outerCallExpression?.resolveCall()?.successfulFunctionCallOrNull()

        val renderedTypeArguments = if (parent is KtValueArgument && resolvedOuterCall != null) {
            outerCallExpression.addTypeArgumentsIfNeeded(element)
        } else {
            null
        }

        if (element.parentValueArgument() as? KtLambdaArgument == null) {
            val renderTypeForProperty = if (parent is KtProperty && parent.typeReference == null) {
                val propertyType = parent.getReturnKtType()
                val symbol = element.singleStatementOrNull()?.resolveCall()?.singleFunctionCallOrNull()?.symbol as? KtFunctionSymbol
                if (symbol != null && symbol.overloadedFunctions(element).size > 1) {
                    propertyType.render(position = Variance.IN_VARIANCE)
                } else null
            } else null

            // Without lambda argument syntax, just replace lambda with reference
            val callableReferenceExpr = psiFactory.createCallableReferenceExpression(referenceName) ?: return null
            return Context(callableReferenceExpr.createSmartPointer(), renderTypeForProperty, renderedTypeArguments)
        } else {
            val symbol = outerCallExpression?.resolveCall()?.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol ?: return null
            val valueParameters = symbol.valueParameters
            val arguments = outerCallExpression.valueArguments.filter { it !is KtLambdaArgument }
            val hadDefaultValues = valueParameters.size - 1 > arguments.size
            val useNamedArguments = valueParameters.any { it.hasDefaultValue } && hadDefaultValues || arguments.any { it.isNamed() }

            val newArgumentList = psiFactory.buildValueArgumentList {
                appendFixedText("(")
                arguments.forEach { argument ->
                    val argumentName = argument.getArgumentName()
                    if (useNamedArguments && argumentName != null) {
                        appendName(argumentName.asName)
                        appendFixedText(" = ")
                    }
                    appendExpression(argument.getArgumentExpression())
                    appendFixedText(", ")
                }
                if (useNamedArguments) {
                    appendName(valueParameters.last().name)
                    appendFixedText(" = ")
                }
                appendNonFormattedText(referenceName)
                appendFixedText(")")
            }
            return Context(newArgumentList.createSmartPointer(), null, renderedTypeArguments)
        }
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtLambdaExpression,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        val newElement = elementContext.newElement.element ?: return
        val parent = element.parent
        val renderedPropertyType = elementContext.renderedPropertyType

        if (parent is KtProperty && renderedPropertyType != null) {
            parent.typeReference = KtPsiFactory(element.project).createType(renderedPropertyType)
            shortenReferences(parent.typeReference!!)
        }

        val outerCallExpression = parent.getStrictParentOfType<KtCallExpression>()
        if (outerCallExpression != null) {
            elementContext.renderedTypeArguments?.let {
                addTypeArguments(outerCallExpression, it, actionContext.project)
                outerCallExpression.typeArgumentList?.let(::shortenReferences)
            }
        }

        when (newElement) {
            is KtCallableReferenceExpression -> (element.replace(newElement) as? KtElement)?.let { shortenReferences(it) }
            is KtValueArgumentList -> {
                val argumentList = outerCallExpression?.valueArgumentList
                val lambdaArgument = element.parentValueArgument() as? KtLambdaArgument
                if (argumentList == null) {
                    (lambdaArgument?.replace(newElement) as? KtElement)?.let {
                        shortenReferences(it)
                    }
                } else {
                    (argumentList.replace(newElement) as? KtValueArgumentList)?.let {
                        shortenReferences(it.arguments.last())
                    }
                    lambdaArgument?.delete()
                }
            }
        }
    }
}

context(KtAnalysisSession)
private fun buildReferenceText(lambdaExpression: KtLambdaExpression): String? {
    val lambdaParameterType = lambdaExpression.lambdaParameterType()
    return when (val singleStatement = lambdaExpression.singleStatementOrNull()) {
        is KtCallExpression -> {
            val calleeReferenceExpression = singleStatement.calleeExpression as? KtNameReferenceExpression ?: return null
            val resolvedCall = calleeReferenceExpression.resolveCall()?.singleCallOrNull<KtCallableMemberCall<*, *>>() ?: return null
            val receiverText = when {
                lambdaParameterType != null && isExtensionFunctionType(lambdaParameterType) -> {
                    calleeReferenceExpression.renderTargetReceiverType()
                }

                else -> ""
            }
            val selectorText = singleStatement.getCallReferencedName() ?: return null
            buildReferenceText(receiverText, selectorText, resolvedCall)
        }

        is KtDotQualifiedExpression -> {
            val (selectorReference, selectorReferenceName) = when (val selector = singleStatement.selectorExpression) {
                is KtCallExpression -> {
                    val callee = selector.calleeExpression as? KtNameReferenceExpression ?: return null
                    callee to callee.getSafeReferencedName()
                }

                is KtNameReferenceExpression -> selector to selector.getSafeReferencedName()
                else -> return null
            }
            val receiver = singleStatement.receiverExpression
            val resolvedCall = singleStatement.selectorExpression?.resolveCall()?.singleCallOrNull<KtCallableMemberCall<*, *>>()
            when (receiver) {
                is KtNameReferenceExpression -> {
                    val receiverSymbol = receiver.resolveCall()?.singleVariableAccessCall()?.partiallyAppliedSymbol?.symbol ?: return null
                    val lambdaValueParameters = lambdaExpression.functionLiteral.getAnonymousFunctionSymbol().valueParameters
                    if (receiverSymbol is KtValueParameterSymbol && receiverSymbol == lambdaValueParameters.firstOrNull()) {
                        val originalReceiverType = receiverSymbol.returnType
                        val receiverText = originalReceiverType.render(position = Variance.IN_VARIANCE)
                        buildReferenceText(receiverText, selectorReferenceName, resolvedCall)
                    } else {
                        val receiverName = receiverSymbol.name.asString()
                        buildReferenceText(receiverName, selectorReferenceName, resolvedCall)
                    }
                }

                else -> {
                    val receiverText = if (lambdaParameterType != null && isExtensionFunctionType(lambdaParameterType)) {
                        selectorReference.renderTargetReceiverType()
                    } else {
                        receiver.text
                    }
                    buildReferenceText(receiverText, selectorReferenceName, resolvedCall)
                }
            }
        }

        else -> null
    }
}

private fun buildReferenceText(receiver: String, selector: String, call: KtCallableMemberCall<*, *>?): String {
    val invokeReference = if (call?.partiallyAppliedSymbol?.symbol?.isInvokeOperator == true) "::invoke" else ""
    return if (receiver.isEmpty()) {
        "::$selector$invokeReference"
    } else {
        "$receiver::$selector$invokeReference"
    }
}

private val KtCallableSymbol.isInvokeOperator: Boolean
    get() = this is KtFunctionSymbol && this.isOperator && name == org.jetbrains.kotlin.util.OperatorNameConventions.INVOKE

private fun getCalleeReferenceExpression(callableExpression: KtExpression): KtNameReferenceExpression? {
    return when (callableExpression) {
        is KtCallExpression -> callableExpression.calleeExpression as? KtNameReferenceExpression
        is KtNameReferenceExpression -> callableExpression
        else -> null
    }
}

context(KtAnalysisSession)
private fun KtLambdaExpression.lambdaParameterType(): KtType? {
    val argument = parentValueArgument() ?: return null
    val callExpression = argument.getStrictParentOfType<KtCallExpression>() ?: return null
    return callExpression.resolveCall()?.successfulFunctionCallOrNull()?.argumentMapping?.get(argument.getArgumentExpression())?.returnType
}

private fun KtLambdaExpression.parentValueArgument(): KtValueArgument? {
    return if (parent is KtLabeledExpression) {
        parent.parent
    } else {
        parent
    } as? KtValueArgument
}

context(KtAnalysisSession)
private fun KtNameReferenceExpression.renderTargetReceiverType(): String {
    val partiallyAppliedSymbol = this.resolveCall()?.successfulCallOrNull<KtCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
    val receiverType = (partiallyAppliedSymbol?.dispatchReceiver ?: partiallyAppliedSymbol?.extensionReceiver)?.type ?: return ""
    return receiverType.render(position = Variance.IN_VARIANCE)
}

context(KtAnalysisSession)
private fun KtExpression.isReferenceToPackage(): Boolean {
    val selectorOrThis = (this as? KtQualifiedExpression)?.selectorExpression ?: this
    val symbols = selectorOrThis.mainReference?.resolveToSymbols() ?: return false
    return symbols.any { it is KtPackageSymbol }
}

context(KtAnalysisSession)
private fun isConvertibleCallInLambdaByAnalyze(
    callableExpression: KtExpression,
    explicitReceiver: KtExpression? = null,
    lambdaExpression: KtLambdaExpression
): Boolean {
    val languageVersionSettings = callableExpression.languageVersionSettings

    val calleeReferenceExpression = getCalleeReferenceExpression(callableExpression) ?: return false

    val partiallyAppliedSymbol =
        calleeReferenceExpression.resolveCall()?.singleCallOrNull<KtCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
    val symbol = partiallyAppliedSymbol?.symbol ?: return false

    if (explicitReceiver?.isReferenceToPackage() == true) return false

    val lambdaParameterType = lambdaExpression.lambdaParameterType()
    if (lambdaParameterType != null && isExtensionFunctionType(lambdaParameterType)) {
        if (explicitReceiver != null && explicitReceiver !is KtThisExpression) return false
    }

    val lambdaParameterIsSuspend = lambdaParameterType?.isSuspendFunctionType == true
    val calleeFunctionIsSuspend = (symbol as? KtFunctionSymbol)?.isSuspend
    if (!lambdaParameterIsSuspend && calleeFunctionIsSuspend == true) return false
    if (lambdaParameterIsSuspend && calleeFunctionIsSuspend == false && !languageVersionSettings.supportsFeature(LanguageFeature.SuspendConversion)) return false

    if (symbol.typeParameters.isNotEmpty() && lambdaExpression.parentValueArgument()?.parent is KtCallExpression) return false

    // No references to Java synthetic properties
    if (!languageVersionSettings.supportsFeature(LanguageFeature.ReferencesToSyntheticJavaProperties) &&
        partiallyAppliedSymbol.symbol is KtSyntheticJavaPropertySymbol
    ) return false

    val hasReceiver = with(partiallyAppliedSymbol) {
        // No references to both member / extension
        if (dispatchReceiver != null && extensionReceiver != null) return false
        dispatchReceiver != null || extensionReceiver != null
    }
    val noBoundReferences = !languageVersionSettings.supportsFeature(LanguageFeature.BoundCallableReferences)
    if (noBoundReferences && hasReceiver && explicitReceiver == null) return false

    val callableArgumentsCount = (callableExpression as? KtCallExpression)?.valueArguments?.size ?: 0
    if (symbol is KtFunctionLikeSymbol && symbol.valueParameters.size != callableArgumentsCount && (lambdaExpression.parentValueArgument() == null || (symbol as? KtFunctionLikeSymbol)?.valueParameters?.none { it.hasDefaultValue } == true)) return false

    if (!lambdaExpression.isArgument() && symbol is KtFunctionSymbol && symbol.overloadedFunctions(lambdaExpression).size > 1) {
        val property = lambdaExpression.getStrictParentOfType<KtProperty>()
        if (property != null && property.initializer?.let(KtPsiUtil::safeDeparenthesize) != lambdaExpression) return false
    }

    val lambdaValueParameterSymbols = lambdaExpression.functionLiteral.getAnonymousFunctionSymbol().valueParameters

    if (explicitReceiver != null && explicitReceiver !is KtSimpleNameExpression &&
        explicitReceiver.anyDescendantOfType<KtSimpleNameExpression> {
            it.resolveCall()?.singleCallOrNull<KtCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.symbol in lambdaValueParameterSymbols
        }
    ) return false

    val explicitReceiverSymbol = (explicitReceiver as? KtNameReferenceExpression)?.resolveCall()
        ?.singleCallOrNull<KtCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.symbol

    if (explicitReceiverSymbol is KtValueParameterSymbol &&
        explicitReceiverSymbol in lambdaValueParameterSymbols && explicitReceiver.isObject()
    ) {
        // Avoid the problem with object's callable references: KT-33885
        return false
    }

    val lambdaParameterAsExplicitReceiver = when (noBoundReferences) {
        true -> explicitReceiver != null
        false -> explicitReceiverSymbol != null && explicitReceiverSymbol == lambdaValueParameterSymbols.firstOrNull()
    }
    val explicitReceiverShift = if (lambdaParameterAsExplicitReceiver) 1 else 0

    val lambdaParametersCount = lambdaValueParameterSymbols.size
    if (lambdaParametersCount != callableArgumentsCount + explicitReceiverShift) return false

    if (explicitReceiver != null && explicitReceiverSymbol is KtValueParameterSymbol && lambdaParameterAsExplicitReceiver) {
        val receiverType = explicitReceiverSymbol.returnType
        // No exotic receiver types
        if (receiverType is KtTypeParameter || receiverType is KtErrorType || receiverType is KtDynamicType || receiverType is KtFunctionalType) return false
    }

    return true
}

context(KtAnalysisSession)
private fun KtExpression.isObject(): Boolean {
    return getKtType()?.expandedClassSymbol?.classKind?.isObject == true
}

private fun isExtensionFunctionType(type: KtType): Boolean {
    val functionalType = type as? KtFunctionalType ?: return false
    return functionalType.hasReceiver
}

context(KtAnalysisSession)
private fun KtFunctionSymbol.overloadedFunctions(lambdaArgument: KtLambdaExpression): List<KtFunctionSymbol> {
    val scope = when (val containingSymbol = this.getContainingSymbol()) {
        is KtClassOrObjectSymbol -> containingSymbol.getMemberScope()
        else -> lambdaArgument.containingKtFile.getScopeContextForPosition(lambdaArgument).getCompositeScope()
    }
    return scope.getCallableSymbols(name).filterIsInstance<KtFunctionSymbol>().toList()
}

context(KtAnalysisSession)
private fun KtCallExpression.addTypeArgumentsIfNeeded(lambda: KtLambdaExpression): String? {
    val resolvedCall = lambda.singleStatementOrNull()?.resolveCall()?.successfulFunctionCallOrNull() ?: return null
    val calledFunctionInLambda = resolvedCall.partiallyAppliedSymbol.symbol as? KtFunctionSymbol ?: return null
    val overloadedFunctions = calledFunctionInLambda.overloadedFunctions(lambda)

    if (overloadedFunctions.count { it.valueParameters.size == calledFunctionInLambda.valueParameters.size } < 2
        && calledFunctionInLambda.typeParameters.isEmpty()
    ) return null

    return getRenderedTypeArguments(this)
}
