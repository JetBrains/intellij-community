// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.collections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.backend.common.descriptors.isSuspend
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ReplaceAssociateFunctionFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.ASSOCIATE
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.ASSOCIATE_TO
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.JOIN_TO
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.MAP
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.MAP_NOT_NULL
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
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainExpressions.Companion.isLiteralValue
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.js.resolve.JsPlatformAnalyzerServices
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

class SimplifiableCallChainInspection : AbstractCallChainChecker() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return qualifiedExpressionVisitor(fun(expression) {
            val callChainExpressions = CallChainExpressions.from(expression) ?: return
            var conversion = findQualifiedConversion(
                callChainExpressions,
                CallChainConversions.conversionGroups
            ) check@{ conversion, firstResolvedCall, _, context ->
                // Do not apply on maps due to lack of relevant stdlib functions
                val firstReceiverType = firstResolvedCall.resultingDescriptor?.extensionReceiverParameter?.type
                if (firstReceiverType != null) {
                    if (conversion.replacement == MAP_NOT_NULL && KotlinBuiltIns.isPrimitiveArray(firstReceiverType)) return@check false
                    val builtIns = context[BindingContext.EXPRESSION_TYPE_INFO, expression]?.type?.builtIns ?: return@check false
                    val firstReceiverRawType = firstReceiverType.constructor.declarationDescriptor?.defaultType
                    if (firstReceiverRawType.isMap(builtIns)) return@check false
                }
                if (conversion.replacement.startsWith(JOIN_TO)) {
                    // Function parameter in map must have String result type
                    if (!firstResolvedCall.hasLastFunctionalParameterWithResult(context) {
                            it.isSubtypeOf(JsPlatformAnalyzerServices.builtIns.charSequence.defaultType)
                        }
                    ) return@check false
                }
                if (conversion.replacement in listOf(MAX_BY, MIN_BY, MIN_BY_OR_NULL, MAX_BY_OR_NULL)) {
                    val functionalArgumentReturnType = firstResolvedCall.lastFunctionalArgument(context)?.returnType ?: return@check false
                    if (functionalArgumentReturnType.isNullable()) return@check false
                }
                if (conversion.removeNotNullAssertion &&
                    conversion.firstName == MAP &&
                    conversion.secondName in listOf(MAX, MAX_OR_NULL, MIN, MIN_OR_NULL)
                ) {
                    val parent = expression.parent
                    if (parent !is KtPostfixExpression || parent.operationToken != KtTokens.EXCLEXCL) return@check false
                }

                if (conversion.firstName == MAP && conversion.secondName == SUM && conversion.replacement == SUM_OF) {
                    val lastFunctionalArgument = firstResolvedCall.lastFunctionalArgument(context) ?: return@check false
                    val type = lastFunctionalArgument.returnType ?: return@check false
                    val isInt = KotlinBuiltIns.isInt(type)
                    if (!isInt && !KotlinBuiltIns.isLong(type) &&
                        !KotlinBuiltIns.isUInt(type) && !KotlinBuiltIns.isULong(type) &&
                        !KotlinBuiltIns.isDouble(type)
                    ) return@check false
                    if (isInt && lastFunctionalArgument.isLambda && lastFunctionalArgument.lastStatement.isLiteralValue()) {
                        // 'sumOf' call with integer literals leads to an overload resolution ambiguity: KT-46360
                        return@check false
                    }
                }
                if (conversion.firstName == MAP && conversion.secondName == TO_MAP) {
                    val argumentSize = expression.callExpression?.valueArguments?.size ?: return@check false
                    if (conversion.replacement == ASSOCIATE && argumentSize != 0
                        || conversion.replacement == ASSOCIATE_TO && argumentSize != 1
                    ) return@check false
                }
                return@check conversion.enableSuspendFunctionCall || !containsSuspendFunctionCall(firstResolvedCall, context)
            } ?: return

            val associateFunction = getAssociateFunction(conversion, expression.receiverExpression)
                ?.let { (associateFunction, associateFunctionName) ->
                    conversion = conversion.copy(replacement = associateFunctionName)
                    associateFunction
                }

            val replacement = conversion.replacement
            val descriptor = holder.manager.createProblemDescriptor(
                expression,
                callChainExpressions.firstCalleeExpression.textRange.shiftRight(-expression.startOffset),
                KotlinBundle.message("call.chain.on.collection.type.may.be.simplified"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly,
                SimplifyCallChainFix(conversion) { callExpression ->
                    val lastArgumentName = if (replacement.startsWith("joinTo")) Name.identifier("transform") else null
                    if (lastArgumentName != null) {
                        val lastArgument = callExpression.valueArgumentList?.arguments?.singleOrNull()
                        val argumentExpression = lastArgument?.getArgumentExpression()
                        if (argumentExpression != null) {
                            lastArgument.replace(createArgument(argumentExpression, lastArgumentName))
                        }
                    }
                    if (associateFunction != null) {
                        ReplaceAssociateFunctionFix.replaceLastStatementForAssociateFunction(callExpression, associateFunction)
                    }
                }
            )
            holder.registerProblem(descriptor)
        })
    }

    private class FunctionalArgument(val isLambda: Boolean, val returnType: KotlinType?, val lastStatement: KtExpression?)

    private fun ResolvedCall<*>.lastFunctionalArgument(context: BindingContext): FunctionalArgument? {
        val argument = valueArguments.entries.lastOrNull()?.value?.arguments?.firstOrNull()
        return when (val argumentExpression = argument?.getArgumentExpression()) {
            is KtLambdaExpression -> {
                val lastStatement = argumentExpression.bodyExpression?.lastBlockStatementOrThis()
                FunctionalArgument(
                    isLambda = true,
                    returnType = lastStatement?.getType(context),
                    lastStatement = lastStatement
                )
            }

            is KtNamedFunction -> {
                FunctionalArgument(
                    isLambda = false,
                    returnType = argumentExpression.typeReference?.let { context[BindingContext.TYPE, it] },
                    lastStatement = argumentExpression.lastBlockStatementOrThis()
                )
            }

            else -> null
        }
    }

    private fun containsSuspendFunctionCall(resolvedCall: ResolvedCall<*>, context: BindingContext): Boolean {
        return resolvedCall.call.callElement.anyDescendantOfType<KtCallExpression> {
            it.getResolvedCall(context)?.resultingDescriptor?.isSuspend == true
        }
    }

    private fun getAssociateFunction(conversion: CallChainConversion, expression: KtExpression): Pair<AssociateFunction, String>? {
        val isAssociateTo = conversion.replacement == ASSOCIATE_TO
        if (conversion.replacement != ASSOCIATE && !isAssociateTo) return null
        if (expression !is KtDotQualifiedExpression) return null
        val (associateFunction, problemHighlightType) =
            AssociateFunctionUtil.getAssociateFunctionAndProblemHighlightType(expression) ?: return null
        if (problemHighlightType == ProblemHighlightType.INFORMATION) return null
        if (associateFunction != AssociateFunction.ASSOCIATE_WITH && associateFunction != AssociateFunction.ASSOCIATE_BY) return null
        return associateFunction to associateFunction.name(isAssociateTo)
    }
}
