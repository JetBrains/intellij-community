// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.collections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.backend.common.descriptors.isSuspend
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.inspections.AssociateFunction
import org.jetbrains.kotlin.idea.inspections.ReplaceAssociateFunctionFix
import org.jetbrains.kotlin.idea.inspections.ReplaceAssociateFunctionInspection
import org.jetbrains.kotlin.idea.inspections.collections.AbstractCallChainChecker.Conversion
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
            var conversion = findQualifiedConversion(expression, conversionGroups) check@{ conversion, firstResolvedCall, _, context ->
                // Do not apply on maps due to lack of relevant stdlib functions
                val firstReceiverType = firstResolvedCall.resultingDescriptor?.extensionReceiverParameter?.type
                if (firstReceiverType != null) {
                    if (conversion.replacement == "mapNotNull" && KotlinBuiltIns.isPrimitiveArray(firstReceiverType)) return@check false
                    val builtIns = context[BindingContext.EXPRESSION_TYPE_INFO, expression]?.type?.builtIns ?: return@check false
                    val firstReceiverRawType = firstReceiverType.constructor.declarationDescriptor?.defaultType
                    if (firstReceiverRawType.isMap(builtIns)) return@check false
                }
                if (conversion.replacement.startsWith("joinTo")) {
                    // Function parameter in map must have String result type
                    if (!firstResolvedCall.hasLastFunctionalParameterWithResult(context) {
                            it.isSubtypeOf(JsPlatformAnalyzerServices.builtIns.charSequence.defaultType)
                        }
                    ) return@check false
                }
                if (conversion.replacement in listOf("maxBy", "minBy", "minByOrNull", "maxByOrNull")) {
                    val functionalArgumentReturnType = firstResolvedCall.lastFunctionalArgument(context)?.returnType ?: return@check false
                    if (functionalArgumentReturnType.isNullable()) return@check false
                }
                if (conversion.removeNotNullAssertion &&
                    conversion.firstName == "map" &&
                    conversion.secondName in listOf("max", "maxOrNull", "min", "minOrNull")
                ) {
                    val parent = expression.parent
                    if (parent !is KtPostfixExpression || parent.operationToken != KtTokens.EXCLEXCL) return@check false
                }

                if (conversion.firstName == "map" && conversion.secondName == "sum" && conversion.replacement == "sumOf") {
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
                if (conversion.firstName == "map" && conversion.secondName == "toMap") {
                    val argumentSize = expression.callExpression?.valueArguments?.size ?: return@check false
                    if (conversion.replacement == "associate" && argumentSize != 0
                        || conversion.replacement == "associateTo" && argumentSize != 1
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
                expression.firstCalleeExpression()!!.textRange.shiftRight(-expression.startOffset),
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

    private fun KtExpression?.isLiteralValue(): Boolean {
        return this != null && when (val expr = KtPsiUtil.safeDeparenthesize(this)) {
            is KtBinaryExpression -> expr.left.isLiteralValue() && expr.right.isLiteralValue()

            is KtIfExpression -> expr.then?.lastBlockStatementOrThis().isLiteralValue() &&
                    expr.`else`?.lastBlockStatementOrThis().isLiteralValue()

            is KtWhenExpression -> expr.entries.all { it.expression?.lastBlockStatementOrThis().isLiteralValue() }

            is KtTryExpression -> expr.tryBlock.lastBlockStatementOrThis().isLiteralValue() &&
                    expr.catchClauses.all { c -> c.catchBody?.lastBlockStatementOrThis().isLiteralValue() }

            else -> expr is KtConstantExpression
        }
    }

    private fun containsSuspendFunctionCall(resolvedCall: ResolvedCall<*>, context: BindingContext): Boolean {
        return resolvedCall.call.callElement.anyDescendantOfType<KtCallExpression> {
            it.getResolvedCall(context)?.resultingDescriptor?.isSuspend == true
        }
    }

    private fun getAssociateFunction(conversion: Conversion, expression: KtExpression): Pair<AssociateFunction, String>? {
        val isAssociateTo = conversion.replacement == "associateTo"
        if (conversion.replacement != "associate" && !isAssociateTo) return null
        if (expression !is KtDotQualifiedExpression) return null
        val (associateFunction, problemHighlightType) =
            ReplaceAssociateFunctionInspection.Util.getAssociateFunctionAndProblemHighlightType(expression) ?: return null
        if (problemHighlightType == ProblemHighlightType.INFORMATION) return null
        if (associateFunction != AssociateFunction.ASSOCIATE_WITH && associateFunction != AssociateFunction.ASSOCIATE_BY) return null
        return associateFunction to associateFunction.name(isAssociateTo)
    }

    private val conversionGroups = conversions.group()
}

private val conversions: List<Conversion> = listOf(
    Conversion("kotlin.collections.filter", "kotlin.collections.first", "first"),
    Conversion("kotlin.collections.filter", "kotlin.collections.firstOrNull", "firstOrNull"),
    Conversion("kotlin.collections.filter", "kotlin.collections.last", "last"),
    Conversion("kotlin.collections.filter", "kotlin.collections.lastOrNull", "lastOrNull"),
    Conversion("kotlin.collections.filter", "kotlin.collections.single", "single"),
    Conversion("kotlin.collections.filter", "kotlin.collections.singleOrNull", "singleOrNull"),
    Conversion("kotlin.collections.filter", "kotlin.collections.isNotEmpty", "any"),
    Conversion("kotlin.collections.filter", "kotlin.collections.List.isEmpty", "none"),
    Conversion("kotlin.collections.filter", "kotlin.collections.count", "count"),
    Conversion("kotlin.collections.filter", "kotlin.collections.any", "any"),
    Conversion("kotlin.collections.filter", "kotlin.collections.none", "none"),
    Conversion("kotlin.collections.sorted", "kotlin.collections.firstOrNull", "min"),
    Conversion("kotlin.collections.sorted", "kotlin.collections.lastOrNull", "max"),
    Conversion("kotlin.collections.sortedDescending", "kotlin.collections.firstOrNull", "max"),
    Conversion("kotlin.collections.sortedDescending", "kotlin.collections.lastOrNull", "min"),
    Conversion("kotlin.collections.sortedBy", "kotlin.collections.firstOrNull", "minBy"),
    Conversion("kotlin.collections.sortedBy", "kotlin.collections.lastOrNull", "maxBy"),
    Conversion("kotlin.collections.sortedByDescending", "kotlin.collections.firstOrNull", "maxBy"),
    Conversion("kotlin.collections.sortedByDescending", "kotlin.collections.lastOrNull", "minBy"),
    Conversion("kotlin.collections.sorted", "kotlin.collections.first", "min", addNotNullAssertion = true),
    Conversion("kotlin.collections.sorted", "kotlin.collections.last", "max", addNotNullAssertion = true),
    Conversion("kotlin.collections.sortedDescending", "kotlin.collections.first", "max", addNotNullAssertion = true),
    Conversion("kotlin.collections.sortedDescending", "kotlin.collections.last", "min", addNotNullAssertion = true),
    Conversion("kotlin.collections.sortedBy", "kotlin.collections.first", "minBy", addNotNullAssertion = true),
    Conversion("kotlin.collections.sortedBy", "kotlin.collections.last", "maxBy", addNotNullAssertion = true),
    Conversion("kotlin.collections.sortedByDescending", "kotlin.collections.first", "maxBy", addNotNullAssertion = true),
    Conversion("kotlin.collections.sortedByDescending", "kotlin.collections.last", "minBy", addNotNullAssertion = true),

    Conversion("kotlin.text.filter", "kotlin.text.first", "first"),
    Conversion("kotlin.text.filter", "kotlin.text.firstOrNull", "firstOrNull"),
    Conversion("kotlin.text.filter", "kotlin.text.last", "last"),
    Conversion("kotlin.text.filter", "kotlin.text.lastOrNull", "lastOrNull"),
    Conversion("kotlin.text.filter", "kotlin.text.single", "single"),
    Conversion("kotlin.text.filter", "kotlin.text.singleOrNull", "singleOrNull"),
    Conversion("kotlin.text.filter", "kotlin.text.isNotEmpty", "any"),
    Conversion("kotlin.text.filter", "kotlin.text.isEmpty", "none"),
    Conversion("kotlin.text.filter", "kotlin.text.count", "count"),
    Conversion("kotlin.text.filter", "kotlin.text.any", "any"),
    Conversion("kotlin.text.filter", "kotlin.text.none", "none"),

    Conversion("kotlin.collections.map", "kotlin.collections.joinTo", "joinTo", enableSuspendFunctionCall = false),
    Conversion("kotlin.collections.map", "kotlin.collections.joinToString", "joinToString", enableSuspendFunctionCall = false),
    Conversion("kotlin.collections.map", "kotlin.collections.filterNotNull", "mapNotNull"),
    Conversion("kotlin.collections.map", "kotlin.collections.toMap", "associate"),
    Conversion("kotlin.collections.map", "kotlin.collections.toMap", "associateTo"),
    Conversion(
        "kotlin.collections.map", "kotlin.collections.sum", "sumOf", replaceableApiVersion = ApiVersion.KOTLIN_1_4
    ),
    Conversion(
        "kotlin.collections.map", "kotlin.collections.max", "maxOf",
        removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
    ),
    Conversion(
        "kotlin.collections.map", "kotlin.collections.max", "maxOfOrNull",
        replaceableApiVersion = ApiVersion.KOTLIN_1_4,
    ),
    Conversion(
        "kotlin.collections.map", "kotlin.collections.maxOrNull", "maxOf",
        removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
    ),
    Conversion(
        "kotlin.collections.map", "kotlin.collections.maxOrNull", "maxOfOrNull",
        replaceableApiVersion = ApiVersion.KOTLIN_1_4,
    ),
    Conversion(
        "kotlin.collections.map", "kotlin.collections.min", "minOf",
        removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
    ),
    Conversion(
        "kotlin.collections.map", "kotlin.collections.min", "minOfOrNull",
        replaceableApiVersion = ApiVersion.KOTLIN_1_4,
    ),
    Conversion(
        "kotlin.collections.map", "kotlin.collections.minOrNull", "minOf",
        removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
    ),
    Conversion(
        "kotlin.collections.map", "kotlin.collections.minOrNull", "minOfOrNull",
        replaceableApiVersion = ApiVersion.KOTLIN_1_4,
    ),
    Conversion(
        "kotlin.collections.mapNotNull", "kotlin.collections.first", "firstNotNullOf",
        replaceableApiVersion = ApiVersion.KOTLIN_1_5
    ),
    Conversion(
        "kotlin.collections.mapNotNull", "kotlin.collections.firstOrNull", "firstNotNullOfOrNull",
        replaceableApiVersion = ApiVersion.KOTLIN_1_5
    ),
    Conversion("kotlin.collections.listOf", "kotlin.collections.filterNotNull", "listOfNotNull")
).map {
    when (val replacement = it.replacement) {
        "min", "max", "minBy", "maxBy" -> {
            val additionalConversion = if ((replacement == "min" || replacement == "max") && it.addNotNullAssertion) {
                it.copy(
                    replacement = "${replacement}Of",
                    replaceableApiVersion = ApiVersion.KOTLIN_1_4,
                    addNotNullAssertion = false,
                    additionalArgument = "{ it }"
                )
            } else {
                it.copy(replacement = "${replacement}OrNull", replaceableApiVersion = ApiVersion.KOTLIN_1_4)
            }
            listOf(additionalConversion, it)
        }

        else -> listOf(it)
    }
}.flatten()
