// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections.isIterable
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertLambdaToReferenceUtils.singleStatementOrNull
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.imports.addImportFor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor

internal abstract class AbstractSimplifiableCallInspection :
    KotlinApplicableInspectionBase.Simple<KtExpression, AbstractSimplifiableCallInspection.Context>() {
    class Context(
        val conversionName: String,
        val replacementCall: String,
        val replacementFqName: FqName,
    )

    protected abstract class Conversion(
        val targetFqName: FqName,
        val replacementFqName: FqName,
    ) {
        val targetShortName = targetFqName.shortName().asString()

        context(_: KaSession)
        abstract fun analyze(callExpression: KtCallExpression): String?

        context(_: KaSession)
        open fun callChecker(resolvedCall: KaFunctionCall<*>): Boolean = true
    }

    protected class FlatMapToFlattenConversion : Conversion(
        targetFqName = StandardKotlinNames.Collections.flatMap,
        replacementFqName = StandardKotlinNames.Collections.flatten,
    ) {
        context(_: KaSession)
        override fun analyze(callExpression: KtCallExpression): String? {
            val lambdaExpression = callExpression.singleLambdaExpression() ?: return null
            if (!lambdaExpression.isIdentityLambda()) return null

            val resolvedCallSymbol =
                callExpression.resolveToCall()?.successfulFunctionCallOrNull()?.partiallyAppliedSymbol ?: return null
            val receiver = resolvedCallSymbol.dispatchReceiver ?: resolvedCallSymbol.extensionReceiver ?: return null
            val receiverType = receiver.type
            if (receiverType.isPrimitiveArray) return null
            val typeArguments = (receiverType as? KaClassType)?.typeArguments
            val receiverTypeArgument = typeArguments?.singleOrNull()?.type ?: return null
            when {
                receiverType.isArray -> if (!receiverTypeArgument.isArray) return null
                else -> if (!receiverTypeArgument.isIterable) return null
            }
            return "flatten()"
        }
    }

    protected class FilterToFilterNotNullConversion(
        targetFqName: FqName = StandardKotlinNames.Collections.filter,
        replacementFqName: FqName = StandardKotlinNames.Collections.filterNotNull,
    ) : Conversion(targetFqName, replacementFqName) {
        context(_: KaSession)
        override fun analyze(callExpression: KtCallExpression): String? {
            val lambdaExpression = callExpression.singleLambdaExpression() ?: return null
            val lambdaParameterName = lambdaExpression.singleLambdaParameterName() ?: return null
            val statement = lambdaExpression.singleStatementOrNull() ?: return null

            if (statement !is KtBinaryExpression) return null
            if (statement.operationToken != KtTokens.EXCLEQ && statement.operationToken != KtTokens.EXCLEQEQEQ) return null

            val left = statement.left ?: return null
            val right = statement.right ?: return null
            if (left.isNameReferenceTo(lambdaParameterName) && right.isNull() ||
                right.isNameReferenceTo(lambdaParameterName) && left.isNull()
            ) {
                return "filterNotNull()"
            }
            return null
        }

        context(_: KaSession)
        override fun callChecker(resolvedCall: KaFunctionCall<*>): Boolean = !resolvedCall.isCalledOnMapExtensionReceiver
    }

    protected class MapNotNullToFilterIsInstanceConversion(
        targetFqName: FqName = FqName("kotlin.collections.mapNotNull"),
        replacementFqName: FqName = StandardKotlinNames.Collections.filterIsInstance,
    ) : Conversion(targetFqName, replacementFqName) {
        context(_: KaSession)
        override fun analyze(callExpression: KtCallExpression): String? {
            val lambdaExpression = callExpression.singleLambdaExpression() ?: return null
            val lambdaParameterName: String? = lambdaExpression.singleLambdaParameterName()
            val statement = lambdaExpression.singleStatementOrNull() ?: return null
            if (statement !is KtBinaryExpressionWithTypeRHS) return null
            val lhs = statement.left as? KtReferenceExpression ?: return null
            if (lhs.text !in listOf("it", lambdaParameterName)) return null
            val rightTypeReference = statement.right ?: return null
            return "filterIsInstance<${rightTypeReference.text}>()"
        }

        context(_: KaSession)
        override fun callChecker(resolvedCall: KaFunctionCall<*>): Boolean = !resolvedCall.isCalledOnMapExtensionReceiver
    }

    protected class FilterToFilterIsInstanceConversion(
        targetFqName: FqName = StandardKotlinNames.Collections.filter,
        replacementFqName: FqName = StandardKotlinNames.Collections.filterIsInstance,
    ) : Conversion(targetFqName, replacementFqName) {
        context(_: KaSession)
        override fun analyze(callExpression: KtCallExpression): String? {
            val lambdaExpression = callExpression.singleLambdaExpression() ?: return null
            val lambdaParameterName = lambdaExpression.singleLambdaParameterName() ?: return null
            val statement = lambdaExpression.singleStatementOrNull() ?: return null

            if (statement !is KtIsExpression) return null

            if (statement.isNegated) return null
            if (!statement.leftHandSide.isNameReferenceTo(lambdaParameterName)) return null
            val rightTypeReference = statement.typeReference ?: return null
            val rightType = rightTypeReference.type

            val resolvedCall = callExpression.resolveToCall()?.successfulFunctionCallOrNull()

            if (resolvedCall != null) {
                val resultingElementType = (resolvedCall.partiallyAppliedSymbol.signature.returnType as? KaClassType)
                    ?.typeArguments?.singleOrNull()?.takeIf { it !is KaStarTypeProjection }?.type
                if (resultingElementType != null && !rightType.isSubtypeOf(resultingElementType)) {
                    return null
                }
            }

            return "filterIsInstance<${rightTypeReference.text}>()"
        }

        context(_: KaSession)
        override fun callChecker(resolvedCall: KaFunctionCall<*>): Boolean = !resolvedCall.isCalledOnMapExtensionReceiver
    }

    protected abstract val conversions: List<Conversion>

    context(_: KaSession)
    private fun KtCallExpression.findConversionsAndResolvedCall(): Pair<List<Conversion>, KaFunctionCall<*>>? {
        val calleeText = calleeExpression?.text ?: return null
        val resolvedCall = this.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val possibleConversions = buildList {
            for (conversion in conversions) {
                if (conversion.targetShortName != calleeText) continue
                if (resolvedCall.symbol.callableId?.asSingleFqName() == conversion.targetFqName) {
                    add(conversion)
                }
            }
        }
        return possibleConversions to resolvedCall
    }

    override fun getProblemDescription(
        element: KtExpression,
        context: Context
    ): @InspectionMessage String = KotlinBundle.message("0.call.could.be.simplified.to.1", context.conversionName, context.replacementCall)


    override fun createQuickFix(
        element: KtExpression,
        context: Context
    ): KotlinModCommandQuickFix<KtExpression> = SimplifyCallFix(context)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = callExpressionVisitor { callExpression ->
        val calleeExpression = callExpression.calleeExpression ?: return@callExpressionVisitor
        visitTargetElement(calleeExpression, holder, isOnTheFly)
    }

    override fun KaSession.prepareContext(element: KtExpression): Context? {
        val callExpression = element.parent as? KtCallExpression ?: return null
        val (conversions, resolvedCall) = callExpression.findConversionsAndResolvedCall() ?: return null
        for (conversion in conversions) {
            if (!conversion.callChecker(resolvedCall)) continue
            val replacementCall = conversion.analyze(callExpression) ?: continue
            return Context(conversion.targetShortName, replacementCall, conversion.replacementFqName)
        }
        return null
    }

    private class SimplifyCallFix(
        val context: Context
    ) : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("simplify.call.fix.text", context.conversionName, context.replacementCall)

        override fun applyFix(
            project: Project,
            element: KtExpression,
            updater: ModPsiUpdater
        ) {
            val containingFile = element.containingKtFile
            val callExpression = element.parent as? KtCallExpression ?: return
            callExpression.replace(KtPsiFactory(project).createExpression(context.replacementCall))
            containingFile.addImportFor(context.replacementFqName)
        }
    }
}

private fun KtCallExpression.singleLambdaExpression(): KtLambdaExpression? {
    val argument = valueArguments.singleOrNull() ?: return null
    return (argument as? KtLambdaArgument)?.getLambdaExpression() ?: argument.getArgumentExpression() as? KtLambdaExpression
}

internal fun KtLambdaExpression.singleLambdaParameterName(): String? {
    val lambdaParameters = valueParameters
    return if (lambdaParameters.isNotEmpty()) lambdaParameters.singleOrNull()?.name else StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
}

private fun KtExpression.isNameReferenceTo(name: String): Boolean =
    this is KtNameReferenceExpression && this.getReferencedName() == name

/**
 * Checks if this lambda expression is an identity lambda (e.g., `{ it }` or `{ x -> x }`).
 *
 * N.B. In the future, consider merging this with
 * [org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.singleReturnedExpressionOrNull] function.
 */
internal fun KtLambdaExpression.isIdentityLambda(): Boolean {
    val reference = singleStatementOrNull() ?: return false
    val lambdaParameterName = singleLambdaParameterName() ?: return false
    return reference.isNameReferenceTo(lambdaParameterName)
}

private fun KtExpression.isNull(): Boolean =
    this is KtConstantExpression && this.node.elementType == KtNodeTypes.NULL

context(_: KaSession)
private val KaType.isPrimitiveArray: Boolean
    get() = this is KaClassType && StandardClassIds.elementTypeByPrimitiveArrayType.containsKey(classId)

context(_: KaSession)
private val KaType.isArray: Boolean
    get() = this is KaClassType && StandardClassIds.Array == classId
