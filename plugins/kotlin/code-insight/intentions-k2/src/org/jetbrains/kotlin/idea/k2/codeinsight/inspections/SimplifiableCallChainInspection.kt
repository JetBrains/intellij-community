// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
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
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversion
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.ASSOCIATE
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.ASSOCIATE_TO
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.JOIN_TO
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.MAP
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.MAX
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.MAX_BY
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.MAX_BY_OR_NULL
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.MAX_OR_NULL
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.MIN
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.MIN_BY
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.MIN_BY_OR_NULL
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.MIN_OR_NULL
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.SUM
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.SUM_OF
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.TO_MAP
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConversionId
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis

class SimplifiableCallChainInspection : KotlinApplicableInspectionBase.Simple<KtQualifiedExpression, SimplifiableCallChainInspection.Context>() {
    class Context(
    )

    override fun getProblemDescription(element: KtQualifiedExpression, context: Context): String {
        return KotlinBundle.message("call.chain.on.collection.type.may.be.simplified")
    }

    override fun createQuickFix(element: KtQualifiedExpression, context: Context): KotlinModCommandQuickFix<KtQualifiedExpression> {
        return object : KotlinModCommandQuickFix<KtQualifiedExpression>() {
            override fun getFamilyName(): String {
                return KotlinBundle.message("call.chain.on.collection.type.may.be.simplified")
            }

            override fun applyFix(project: Project, element: KtQualifiedExpression, updater: ModPsiUpdater) {

            }
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> {
        return qualifiedExpressionVisitor { qualifiedExpression ->
            visitTargetElement(qualifiedExpression, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtQualifiedExpression): Boolean {
        val firstCallExpression = getCallExpression(element.receiverExpression) ?: return false
        // Do not apply for lambdas with return inside
        if (firstCallExpression.lambdaArguments.singleOrNull()?.anyDescendantOfType<KtReturnExpression>() == true) return false
        if (getConversionId(element) == null) return false

        return true
    }

    context(KaSession)
    override fun prepareContext(element: KtQualifiedExpression): Context? {
        val conversionId = getConversionId(element) ?: return null
        val candidateConversions = getPotentialConversions(element, conversionId).ifEmpty { return null }

        val firstCall = element.receiverExpression.resolveToCall() ?: return null
        val secondCall = element.resolveToCall() ?: return null

        if (secondCall.containsFunctionalArgumentsOfAnyKind()) return null

        val matchingConversion = candidateConversions.firstOrNull { conversion ->
            firstCall.isCalling(conversion.firstFqName)
                    && secondCall.isCalling(conversion.secondFqName)
                    && isConversionApplicable(element, conversion, firstCall, secondCall)
        } ?: return null

        return Context()
    }

    private fun getCallExpression(firstExpression: KtExpression): KtCallExpression? =
        (firstExpression as? KtQualifiedExpression)?.selectorExpression as? KtCallExpression
            ?: firstExpression as? KtCallExpression

    private fun getConversionId(expression: KtQualifiedExpression): ConversionId? {
        val firstExpression = expression.receiverExpression
        val firstCallExpression = getCallExpression(firstExpression) ?: return null
        val firstCalleeExpression = firstCallExpression.calleeExpression ?: return null
        val secondCallExpression = expression.selectorExpression as? KtCallExpression ?: return null
        val secondCalleeExpression = secondCallExpression.calleeExpression ?: return null

        return ConversionId(firstCalleeExpression, secondCalleeExpression)
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
        if (isSumOfConversionWithAmbiguousIlt(conversion, firstCall)) return false
        if (isAssociateConversionWithWrongArgumentCount(conversion, secondCall)) return false
        if (isSuspendForbiddenButFound(conversion, firstCall)) return false

        return true
    }

    private fun isRequiredNotNullAssertionMissing(
        conversion: CallChainConversion,
        expression: KtQualifiedExpression
    ): Boolean {
        if (!conversion.removeNotNullAssertion) return false
        if (conversion.firstName != MAP || conversion.secondName !in listOf(MAX, MAX_OR_NULL, MIN, MIN_OR_NULL)) return false
        val parentPostfixExpression = expression.parent as? KtPostfixExpression ?: return false
        return parentPostfixExpression.operationToken != KtTokens.EXCLEXCL
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun isMapNotNullOnPrimitiveArrayConversion(conversion: CallChainConversion, firstCall: KaCallInfo): Boolean {
        if (conversion.replacement != CallChainConversions.MAP_NOT_NULL) return false
        val extensionReceiverType = firstCall.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.extensionReceiver?.type ?: return false
        return extensionReceiverType.isArrayOrPrimitiveArray && extensionReceiverType.symbol?.typeParameters.orEmpty().isEmpty()
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun isAppliedOnMapReceiver(firstCall: KaCallInfo): Boolean {
        val extensionReceiverType = firstCall.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.extensionReceiver?.type ?: return false
        return extensionReceiverType.isSubTypeOfClassId(ClassId.topLevel(StandardNames.FqNames.map))
    }

    context(KaSession)
    private fun isJoinToConversionWithNonMatchingFirstLambda(conversion: CallChainConversion, firstCall: KaCallInfo): Boolean {
        if (!conversion.replacement.startsWith(JOIN_TO)) return false
        val lambdaArgSignature = firstCall.lastLambdaArgumentSignatureOrNull() ?: return false
        val lambdaType = lambdaArgSignature.returnType as? KaFunctionType ?: return false
        return !lambdaType.returnType.isSubTypeOfClassId(ClassId.topLevel(StandardNames.FqNames.charSequence.toSafe()))
    }

    context(KaSession)
    private fun isMaxMinByConversionWithNullableFirstLambda(conversion: CallChainConversion, firstCall: KaCallInfo): Boolean {
        if (conversion.replacement !in listOf(MAX_BY, MIN_BY, MAX_BY_OR_NULL, MIN_BY_OR_NULL)) return false
        val lambdaArgSignature = firstCall.lastLambdaArgumentSignatureOrNull() ?: return false
        return lambdaArgSignature.returnType.lambdaReturnTypeOrNull()?.isMarkedNullable == true
    }

    context(KaSession)
    private fun isAssociateConversionWithWrongArgumentCount(conversion: CallChainConversion, secondCall: KaCallInfo): Boolean {
        if (conversion.firstName != MAP || conversion.secondName != TO_MAP) return false
        val argCount = secondCall.successfulFunctionCallOrNull()?.argumentMapping?.size ?: return false
        return conversion.replacement == ASSOCIATE && argCount != 0 || conversion.replacement == ASSOCIATE_TO && argCount != 1
    }

    context(KaSession)
    private fun isSuspendForbiddenButFound(conversion: CallChainConversion, firstCall: KaCallInfo): Boolean {
        if (conversion.enableSuspendFunctionCall) return false
        val callArguments = firstCall.successfulFunctionCallOrNull()?.argumentMapping ?: return false
        return callArguments.keys.any { argumentExpression ->
            argumentExpression.anyDescendantOfType<KtCallExpression> { subCallExpr -> subCallExpr.isSuspendCall() }
        }
    }

    // Integer Literal Type overload resolution ambiguity, see KT-46360
    context(KaSession)
    private fun isSumOfConversionWithAmbiguousIlt(conversion: CallChainConversion, firstCall: KaCallInfo): Boolean {
        if (conversion.firstName != MAP || conversion.secondName != SUM || conversion.replacement != SUM_OF) return false
        val lambdaArgSignature = firstCall.lastLambdaArgumentSignatureOrNull() ?: return false
        val lambdaReturnType = lambdaArgSignature.returnType.lambdaReturnTypeOrNull() ?: return false
        if (!lambdaReturnType.isSignedOrUnsignedIntegerType()) return false
        val lastArgExpression = firstCall.successfulFunctionCallOrNull()?.argumentMapping?.keys?.lastOrNull() ?: return false
        val lastStatement = (lastArgExpression as? KtLambdaExpression)?.bodyExpression?.lastBlockStatementOrThis() ?: return false
        with (CallChainConversions) { if (lastStatement.isLiteralValue()) return true }
        return false
    }
    // endregion

    // region AA utilities
    context(KaSession)
    private fun KaType.isSubTypeOfClassId(wantedType: ClassId) =
        this is KaClassType && classId == wantedType ||
                allSupertypes.any { type -> type is KaClassType && type.classId == wantedType }

    context(KaSession)
    private fun KaCallInfo.isCalling(fqName: FqName): Boolean =
        successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol?.importableFqName == fqName

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
    private fun KaCallInfo.lastLambdaArgumentSignatureOrNull(): KaVariableSignature<KaValueParameterSymbol>? {
        return successfulFunctionCallOrNull()?.argumentMapping?.values?.lastOrNull()?.takeIf { it.isFunctionalTypeOfAnyKind() }
    }

    context(KaSession)
    private fun KaType.lambdaReturnTypeOrNull(): KaType? = (this as? KaFunctionType)?.returnType

    context(KaSession)
    private fun KaType.isSignedOrUnsignedIntegerType(): Boolean {
        return isByteType || isShortType || isIntType || isLongType
                || isUByteType || isUShortType || isUIntType || isULongType
    }

    context(KaSession)
    private fun KtCallExpression.isSuspendCall(): Boolean {
        val resolvedCall = resolveToCall() ?: return false
        val symbol = resolvedCall.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol ?: return false
        return symbol is KaNamedFunctionSymbol && symbol.isSuspend
    }
    // endregion
}
