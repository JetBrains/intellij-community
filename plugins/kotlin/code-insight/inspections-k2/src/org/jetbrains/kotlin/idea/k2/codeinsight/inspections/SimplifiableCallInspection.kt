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
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.SimplifiableCallInspection.Context
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*

internal class SimplifiableCallInspection : KotlinApplicableInspectionBase.Simple<KtExpression, Context>() {
    class Context(
        val conversionName: String,
        val replacement: String,
    )

    internal object Holder {
        abstract class Conversion(
            callFqName: String
        ) {
            val fqName = FqName(callFqName)

            val shortName = fqName.shortName().asString()

            context(_: KaSession)
            abstract fun analyze(callExpression: KtCallExpression): String?

            context(_: KaSession)
            open fun callChecker(resolvedCall: KaFunctionCall<*>): Boolean = true
        }

        private val flattenConversion = object : Conversion("kotlin.collections.flatMap") {
            context(_: KaSession)
            override fun analyze(callExpression: KtCallExpression): String? {
                val lambdaExpression = callExpression.singleLambdaExpression() ?: return null
                val reference = lambdaExpression.singleStatement() ?: return null
                val lambdaParameterName = lambdaExpression.singleLambdaParameterName() ?: return null
                if (!reference.isNameReferenceTo(lambdaParameterName)) return null
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

        private val filterConversion = object : Conversion("kotlin.collections.filter") {
            context(_: KaSession)
            override fun analyze(callExpression: KtCallExpression): String? {
                val lambdaExpression = callExpression.singleLambdaExpression() ?: return null
                val lambdaParameterName = lambdaExpression.singleLambdaParameterName() ?: return null
                val statement = lambdaExpression.singleStatement() ?: return null
                when (statement) {
                    is KtBinaryExpression -> {
                        if (statement.operationToken != KtTokens.EXCLEQ && statement.operationToken != KtTokens.EXCLEQEQEQ) return null
                        val left = statement.left ?: return null
                        val right = statement.right ?: return null
                        if (left.isNameReferenceTo(lambdaParameterName) && right.isNull() ||
                            right.isNameReferenceTo(lambdaParameterName) && left.isNull()
                        ) {
                            return "filterNotNull()"
                        }
                    }
                    is KtIsExpression -> {
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
                }

                return null
            }

            context(_: KaSession)
            override fun callChecker(resolvedCall: KaFunctionCall<*>): Boolean {
                val extensionReceiverType = resolvedCall.partiallyAppliedSymbol.extensionReceiver?.type ?: return false
                return !extensionReceiverType.isMap
            }
        }

        val conversions: List<Conversion> = listOf(
            flattenConversion,
            filterConversion,
        )
    }

    context(_: KaSession)
    private fun KtCallExpression.findConversionAndResolvedCall(): Pair<Holder.Conversion, KaFunctionCall<*>>? {
        val calleeText = calleeExpression?.text ?: return null
        val resolvedCall: KaFunctionCall<*>? by lazy { this.resolveToCall()?.successfulFunctionCallOrNull() }
        for (conversion in Holder.conversions) {
            if (conversion.shortName != calleeText) continue
            if (resolvedCall?.symbol?.callableId?.asSingleFqName() == conversion.fqName) {
                return conversion to resolvedCall!!
            }
        }
        return null
    }

    override fun getProblemDescription(
        element: KtExpression,
        context: Context
    ): @InspectionMessage String = KotlinBundle.message("0.call.could.be.simplified.to.1", context.conversionName, context.replacement)



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
        val (conversion, resolvedCall) = callExpression.findConversionAndResolvedCall() ?: return null
        if (!conversion.callChecker(resolvedCall)) return null
        val replacement = conversion.analyze(callExpression) ?: return null
        return Context(conversion.shortName, replacement)
    }

    private class SimplifyCallFix(
        val context: Context
    ) : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("simplify.call.fix.text", context.conversionName, context.replacement)

        override fun applyFix(
            project: Project,
            element: KtExpression,
            updater: ModPsiUpdater
        ) {
            val callExpression = element.parent as? KtCallExpression ?: return
            callExpression.replace(KtPsiFactory(project).createExpression(context.replacement))
        }
    }
}

private fun KtCallExpression.singleLambdaExpression(): KtLambdaExpression? {
    val argument = valueArguments.singleOrNull() ?: return null
    return (argument as? KtLambdaArgument)?.getLambdaExpression() ?: argument.getArgumentExpression() as? KtLambdaExpression
}

private fun KtLambdaExpression.singleStatement(): KtExpression? = bodyExpression?.statements?.singleOrNull()

private fun KtLambdaExpression.singleLambdaParameterName(): String? {
    val lambdaParameters = valueParameters
    return if (lambdaParameters.isNotEmpty()) lambdaParameters.singleOrNull()?.name else StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
}

private fun KtExpression.isNameReferenceTo(name: String): Boolean =
    this is KtNameReferenceExpression && this.getReferencedName() == name

private fun KtExpression.isNull(): Boolean =
    this is KtConstantExpression && this.node.elementType == KtNodeTypes.NULL

context(_: KaSession)
private val KaType.isPrimitiveArray: Boolean
    get() = this is KaClassType && StandardClassIds.elementTypeByPrimitiveArrayType.containsKey(classId)

context(_: KaSession)
private val KaType.isArray: Boolean
    get() = this is KaClassType && StandardClassIds.Array == classId

context(_: KaSession)
private val KaType.isMap: Boolean
    get() = this is KaClassType && (classId == StandardClassIds.Map || isSubtypeOf(StandardClassIds.Map))
