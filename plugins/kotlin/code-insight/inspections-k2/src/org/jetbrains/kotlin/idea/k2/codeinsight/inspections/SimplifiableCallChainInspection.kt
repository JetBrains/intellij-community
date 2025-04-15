// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversion
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainExpressions
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainExpressions.Companion.isLiteralValue
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConversionId
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyCallChainFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis
import org.jetbrains.kotlin.psi.qualifiedExpressionVisitor

class SimplifiableCallChainInspection : KotlinApplicableInspectionBase.Simple<KtQualifiedExpression, CallChainConversion>() {
    override fun getProblemDescription(element: KtQualifiedExpression, context: CallChainConversion): String {
        return KotlinBundle.message("call.chain.on.collection.type.may.be.simplified")
    }

    override fun createQuickFix(
      element: KtQualifiedExpression,
      context: CallChainConversion,
    ): KotlinModCommandQuickFix<KtQualifiedExpression> = SimplifyCallChainFix(
      context,
      modifyArguments = { callExpression ->
        if (context.replacement.startsWith(CallChainConversions.JOIN_TO)) {
          val lastArgument = callExpression.valueArgumentList?.arguments?.singleOrNull()
          val argumentExpression = lastArgument?.getArgumentExpression()
          if (argumentExpression != null) {
            lastArgument.replace(createArgument(argumentExpression, Name.identifier("transform")))
          }
        }
      }
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> {
        return qualifiedExpressionVisitor { qualifiedExpression ->
          visitTargetElement(qualifiedExpression, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtQualifiedExpression): Boolean {
        val callChainExpressions = CallChainExpressions.Companion.from(element)
        if (callChainExpressions == null) return false
        // Do not apply for lambdas with return inside
        if (callChainExpressions.firstCallExpression.lambdaArguments.singleOrNull()?.anyDescendantOfType<KtReturnExpression>() == true)
            return false

        return true
    }

    override fun KaSession.prepareContext(element: KtQualifiedExpression): CallChainConversion? {
        val callChainExpressions = CallChainExpressions.Companion.from(element) ?: return null
        val conversionId = ConversionId(callChainExpressions.firstCalleeExpression, callChainExpressions.secondCalleeExpression)
        val candidateConversions = getPotentialConversions(element, conversionId).ifEmpty { return null }

        val firstCall = callChainExpressions.firstCalleeExpression.resolveToCall() ?: return null
        val secondCall = callChainExpressions.secondCalleeExpression.resolveToCall() ?: return null

        if (secondCall.containsFunctionalArgumentsOfAnyKind()) return null

        val matchingConversion = candidateConversions.firstOrNull { conversion ->
            firstCall.isCalling(conversion.firstFqName)
                    && secondCall.isCalling(conversion.secondFqName)
                    && isConversionApplicable(element, conversion, firstCall, secondCall)
        } ?: return null

        return matchingConversion
    }

    private fun getPotentialConversions(expression: KtQualifiedExpression, conversionId: ConversionId): List<CallChainConversion> {
        val apiVersion by lazy { expression.languageVersionSettings.apiVersion }
        return CallChainConversions.conversionGroups[conversionId]?.filter { conversion ->
            val replaceableApiVersion = conversion.replaceableApiVersion
            replaceableApiVersion == null || apiVersion >= replaceableApiVersion
        }?.sortedByDescending { it.removeNotNullAssertion }.orEmpty()
    }

    // region isConversionApplicable
    context(KaSession)
    private fun isConversionApplicable(
      expression: KtQualifiedExpression,
      conversion: CallChainConversion,
      firstCall: KaCallInfo,
      secondCall: KaCallInfo,
    ): Boolean {
        if (isRequiredNotNullAssertionMissing(conversion, expression)) return false
        if (isMapNotNullOnPrimitiveArrayConversion(conversion, firstCall)) return false
        if (isAppliedOnMapReceiver(firstCall)) return false
        if (isJoinToConversionWithNonMatchingFirstLambda(conversion, firstCall)) return false
        if (isMaxMinByConversionWithNullableFirstLambda(conversion, firstCall)) return false
        if (isInapplicableSumOfConversion(conversion, firstCall)) return false
        if (isAssociateConversionWithWrongArgumentCount(conversion, secondCall)) return false
        if (isSuspendForbiddenButFound(conversion, firstCall)) return false

        return true
    }

    private fun isRequiredNotNullAssertionMissing(
      conversion: CallChainConversion,
      expression: KtQualifiedExpression
    ): Boolean {
        if (!conversion.removeNotNullAssertion) return false
        if (conversion.firstName != CallChainConversions.MAP || conversion.secondName !in listOf(CallChainConversions.MAX,
                                                                                                 CallChainConversions.MAX_OR_NULL,
                                                                                                 CallChainConversions.MIN,
                                                                                                 CallChainConversions.MIN_OR_NULL)) return false
        val parentPostfixExpression = expression.parent as? KtPostfixExpression ?: return true
        return parentPostfixExpression.operationToken != KtTokens.EXCLEXCL
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun isMapNotNullOnPrimitiveArrayConversion(conversion: CallChainConversion, firstCall: KaCallInfo): Boolean {
        if (conversion.replacement != CallChainConversions.MAP_NOT_NULL) return false
        val extensionReceiverType = firstCall.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.extensionReceiver?.type
            ?: return false
        return extensionReceiverType.isArrayOrPrimitiveArray && extensionReceiverType.symbol?.typeParameters.orEmpty().isEmpty()
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun isAppliedOnMapReceiver(firstCall: KaCallInfo): Boolean {
        val extensionReceiverType = firstCall.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.extensionReceiver?.type
            ?: return false
        return extensionReceiverType.isSubTypeOfClassId(ClassId.Companion.topLevel(StandardNames.FqNames.map))
    }

    context(KaSession)
    private fun isJoinToConversionWithNonMatchingFirstLambda(conversion: CallChainConversion, firstCall: KaCallInfo): Boolean {
        if (!conversion.replacement.startsWith(CallChainConversions.JOIN_TO)) return false
        val lambdaArgSignature = firstCall.lastFunctionalArgumentSignatureOrNull() ?: return false
        val lambdaType = lambdaArgSignature.returnType as? KaFunctionType ?: return false
        return !lambdaType.returnType.isSubTypeOfClassId(ClassId.Companion.topLevel(StandardNames.FqNames.charSequence.toSafe()))
    }

    /**
     * Lambda argument type is not precise enough: for most of the cases it will be locked before lambda analysis.
     * Which means that the actual type of the expression returned by the lambda can be not nullable with a nullable lambda return type.
     * Since the inspection ignores lambdas with returns, it's safe to check only the last expression's type.
     */
    context(KaSession)
    private fun isMaxMinByConversionWithNullableFirstLambda(conversion: CallChainConversion, firstCall: KaCallInfo): Boolean {
        if (conversion.replacement !in listOf(CallChainConversions.MAX_BY, CallChainConversions.MIN_BY, CallChainConversions.MAX_BY_OR_NULL,
                                              CallChainConversions.MIN_BY_OR_NULL)) return false
        val lastLambdaArgumentExpression = firstCall.lastLambdaArgumentExpressionOrNull() ?: return false
        return lastLambdaArgumentExpression.bodyExpression?.lastBlockStatementOrThis()?.expressionType?.isMarkedNullable == true
    }

    context(KaSession)
    private fun isAssociateConversionWithWrongArgumentCount(conversion: CallChainConversion, secondCall: KaCallInfo): Boolean {
        if (conversion.firstName != CallChainConversions.MAP || conversion.secondName != CallChainConversions.TO_MAP) return false
        val argCount = secondCall.successfulFunctionCallOrNull()?.argumentMapping?.size ?: return false
        return conversion.replacement == CallChainConversions.ASSOCIATE && argCount != 0 || conversion.replacement == CallChainConversions.ASSOCIATE_TO && argCount != 1
    }

    context(KaSession)
    private fun isSuspendForbiddenButFound(conversion: CallChainConversion, firstCall: KaCallInfo): Boolean {
        if (conversion.enableSuspendFunctionCall) return false
        val callArguments = firstCall.successfulFunctionCallOrNull()?.argumentMapping ?: return false
        return callArguments.keys.any { argumentExpression ->
            argumentExpression.anyDescendantOfType<KtCallExpression> { subCallExpr -> subCallExpr.isSuspendCall() }
        }
    }

    context(KaSession)
    private fun isInapplicableSumOfConversion(conversion: CallChainConversion, firstCall: KaCallInfo): Boolean {
        if (conversion.firstName != CallChainConversions.MAP || conversion.secondName != CallChainConversions.SUM || conversion.replacement != CallChainConversions.SUM_OF) return false
        val (functionalArgumentExpr, signature) = firstCall.lastFunctionalArgumentWithSignatureOrNull() ?: return false
        val lambdaReturnType = signature.returnType.lambdaReturnTypeOrNull() ?: return false
        if (!lambdaReturnType.isApplicableTypeForSumOf()) return true

        // Extra check for Integer Literal Type overload resolution ambiguity, see KT-46360
        if (functionalArgumentExpr !is KtLambdaExpression) return false
        val lastStatement = functionalArgumentExpr.bodyExpression?.lastBlockStatementOrThis() ?: return false
        return lambdaReturnType.isIntType && lastStatement.isLiteralValue()
    }
    // endregion

    // region AA utilities
    context(KaSession)
    private fun KaType.isSubTypeOfClassId(wantedType: ClassId) =
      this is KaClassType && classId == wantedType ||
      allSupertypes.any { type -> type is KaClassType && type.classId == wantedType }

    context(KaSession)
    private fun KaCallInfo.isCalling(fqName: FqName): Boolean =
        successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol?.getFqNameIfPackageOrNonLocal() == fqName

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun KaCallableSignature<*>.isFunctionalTypeOfAnyKind(): Boolean =
        returnType.functionTypeKind != null

    context(KaSession)
    private fun KaCallInfo.containsFunctionalArgumentsOfAnyKind(): Boolean {
        val functionCall = successfulFunctionCallOrNull() ?: return false
        return functionCall.argumentMapping.any { (_, argSignature) ->
            argSignature.isFunctionalTypeOfAnyKind()
        }
    }

    context(KaSession)
    private fun KaCallInfo.lastFunctionalArgumentWithSignatureOrNull(): Pair<KtExpression, KaVariableSignature<KaValueParameterSymbol>>? {
        val (argument, signature) = successfulFunctionCallOrNull()?.argumentMapping?.entries?.lastOrNull() ?: return null
        if (!signature.isFunctionalTypeOfAnyKind()) return null
        return argument to signature
    }

    context(KaSession)
    private fun KaCallInfo.lastFunctionalArgumentSignatureOrNull(): KaVariableSignature<KaValueParameterSymbol>? =
        lastFunctionalArgumentWithSignatureOrNull()?.second

    context(KaSession)
    private fun KaCallInfo.lastLambdaArgumentExpressionOrNull(): KtLambdaExpression? =
        lastFunctionalArgumentWithSignatureOrNull()?.first as? KtLambdaExpression

    context(KaSession)
    private fun KaType.lambdaReturnTypeOrNull(): KaType? =
      (this as? KaFunctionType)?.returnType

    context(KaSession)
    private fun KaType.isApplicableTypeForSumOf(): Boolean =
        isIntType || isUIntType || isLongType || isULongType || isDoubleType

    context(KaSession)
    private fun KtCallExpression.isSuspendCall(): Boolean {
        val resolvedCall = resolveToCall() ?: return false
        val symbol = resolvedCall.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol ?: return false
        return symbol is KaNamedFunctionSymbol && symbol.isSuspend
    }
    // endregion
}