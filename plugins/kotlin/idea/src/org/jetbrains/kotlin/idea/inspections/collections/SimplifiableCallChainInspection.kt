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
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.ASSOCIATE
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.ASSOCIATE_TO
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.JOIN_TO
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.MAP
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.MAP_NOT_NULL
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.MAX
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.MAX_BY
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.MAX_BY_OR_NULL
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.MAX_OR_NULL
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.MIN
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.MIN_BY
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.MIN_BY_OR_NULL
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.MIN_OR_NULL
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.SUM
import org.jetbrains.kotlin.idea.inspections.collections.CallChainConversionInspectionStringNames.SUM_OF
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
                if (conversion.firstName == MAP && conversion.secondName == "toMap") {
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
        val isAssociateTo = conversion.replacement == ASSOCIATE_TO
        if (conversion.replacement != ASSOCIATE && !isAssociateTo) return null
        if (expression !is KtDotQualifiedExpression) return null
        val (associateFunction, problemHighlightType) =
            ReplaceAssociateFunctionInspection.Util.getAssociateFunctionAndProblemHighlightType(expression) ?: return null
        if (problemHighlightType == ProblemHighlightType.INFORMATION) return null
        if (associateFunction != AssociateFunction.ASSOCIATE_WITH && associateFunction != AssociateFunction.ASSOCIATE_BY) return null
        return associateFunction to associateFunction.name(isAssociateTo)
    }

    private val conversionGroups = conversions.group()
}

object CallChainConversionInspectionStringNames {
    const val KOTLIN_COLLECTIONS_ANY = "kotlin.collections.any"
    const val KOTLIN_COLLECTIONS_COUNT = "kotlin.collections.count"
    const val KOTLIN_COLLECTIONS_FILTER = "kotlin.collections.filter"
    const val KOTLIN_COLLECTIONS_FILTER_NOT_NULL = "kotlin.collections.filterNotNull"
    const val KOTLIN_COLLECTIONS_FIRST = "kotlin.collections.first"
    const val KOTLIN_COLLECTIONS_FIRST_OR_NULL = "kotlin.collections.firstOrNull"
    const val KOTLIN_COLLECTIONS_IS_NOT_EMPTY = "kotlin.collections.isNotEmpty"
    const val KOTLIN_COLLECTIONS_JOIN_TO = "kotlin.collections.joinTo"
    const val KOTLIN_COLLECTIONS_JOIN_TO_STRING = "kotlin.collections.joinToString"
    const val KOTLIN_COLLECTIONS_LAST = "kotlin.collections.last"
    const val KOTLIN_COLLECTIONS_LAST_OR_NULL = "kotlin.collections.lastOrNull"
    const val KOTLIN_COLLECTIONS_LIST_IS_EMPTY = "kotlin.collections.List.isEmpty"
    const val KOTLIN_COLLECTIONS_LIST_OF = "kotlin.collections.listOf"
    const val KOTLIN_COLLECTIONS_MAP = "kotlin.collections.map"
    const val KOTLIN_COLLECTIONS_MAP_NOT_NULL = "kotlin.collections.mapNotNull"
    const val KOTLIN_COLLECTIONS_MAX = "kotlin.collections.max"
    const val KOTLIN_COLLECTIONS_MAX_OR_NULL = "kotlin.collections.maxOrNull"
    const val KOTLIN_COLLECTIONS_MIN = "kotlin.collections.min"
    const val KOTLIN_COLLECTIONS_MIN_OR_NULL = "kotlin.collections.minOrNull"
    const val KOTLIN_COLLECTIONS_NONE = "kotlin.collections.none"
    const val KOTLIN_COLLECTIONS_SINGLE = "kotlin.collections.single"
    const val KOTLIN_COLLECTIONS_SINGLE_OR_NULL = "kotlin.collections.singleOrNull"
    const val KOTLIN_COLLECTIONS_SORTED = "kotlin.collections.sorted"
    const val KOTLIN_COLLECTIONS_SORTED_BY = "kotlin.collections.sortedBy"
    const val KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING = "kotlin.collections.sortedByDescending"
    const val KOTLIN_COLLECTIONS_SORTED_DESCENDING = "kotlin.collections.sortedDescending"
    const val KOTLIN_COLLECTIONS_SUM = "kotlin.collections.sum"
    const val KOTLIN_COLLECTIONS_TO_MAP = "kotlin.collections.toMap"
    const val KOTLIN_TEXT_ANY = "kotlin.text.any"
    const val KOTLIN_TEXT_COUNT = "kotlin.text.count"
    const val KOTLIN_TEXT_FILTER = "kotlin.text.filter"
    const val KOTLIN_TEXT_FIRST = "kotlin.text.first"
    const val KOTLIN_TEXT_FIRST_OR_NULL = "kotlin.text.firstOrNull"
    const val KOTLIN_TEXT_IS_EMPTY = "kotlin.text.isEmpty"
    const val KOTLIN_TEXT_IS_NOT_EMPTY = "kotlin.text.isNotEmpty"
    const val KOTLIN_TEXT_LAST = "kotlin.text.last"
    const val KOTLIN_TEXT_LAST_OR_NULL = "kotlin.text.lastOrNull"
    const val KOTLIN_TEXT_NONE = "kotlin.text.none"
    const val KOTLIN_TEXT_SINGLE = "kotlin.text.single"
    const val KOTLIN_TEXT_SINGLE_OR_NULL = "kotlin.text.singleOrNull"

    // replacements
    const val FIRST = "first"
    const val FIRST_OR_NULL = "firstOrNull"
    const val LAST = "last"
    const val LAST_OR_NULL = "lastOrNull"
    const val SINGLE = "single"
    const val SINGLE_OR_NULL = "singleOrNull"
    const val ANY = "any"
    const val NONE = "none"
    const val COUNT = "count"
    const val MIN = "min"
    const val MAX = "max"
    const val MIN_OR_NULL = "minOrNull"
    const val MAX_OR_NULL = "maxOrNull"
    const val MIN_BY = "minBy"
    const val MAX_BY = "maxBy"
    const val MIN_BY_OR_NULL = "minByOrNull"
    const val MAX_BY_OR_NULL = "maxByOrNull"
    const val JOIN_TO = "joinTo"
    const val JOIN_TO_STRING = "joinToString"
    const val MAP_NOT_NULL = "mapNotNull"
    const val ASSOCIATE = "associate"
    const val ASSOCIATE_TO = "associateTo"
    const val SUM_OF = "sumOf"
    const val MAX_OF = "maxOf"
    const val MAX_OF_OR_NULL = "maxOfOrNull"
    const val MIN_OF = "minOf"
    const val MIN_OF_OR_NULL = "minOfOrNull"
    const val FIRST_NOT_NULL_OF = "firstNotNullOf"
    const val FIRST_NOT_NULL_OF_OR_NULL = "firstNotNullOfOrNull"
    const val LIST_OF_NOT_NULL = "listOfNotNull"
    const val MAP = "map"
    const val SUM = "sum"
}

private val conversions: List<Conversion> = with(CallChainConversionInspectionStringNames) {
    listOf(
        Conversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_FIRST, FIRST),
        Conversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_FIRST_OR_NULL, FIRST_OR_NULL),
        Conversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_LAST, LAST),
        Conversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_LAST_OR_NULL, LAST_OR_NULL),
        Conversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_SINGLE, SINGLE),
        Conversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_SINGLE_OR_NULL, SINGLE_OR_NULL),
        Conversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_IS_NOT_EMPTY, ANY),
        Conversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_LIST_IS_EMPTY, NONE),
        Conversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_COUNT, COUNT),
        Conversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_ANY, ANY),
        Conversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_NONE, NONE),
        Conversion(KOTLIN_COLLECTIONS_SORTED, KOTLIN_COLLECTIONS_FIRST_OR_NULL, MIN),
        Conversion(KOTLIN_COLLECTIONS_SORTED, KOTLIN_COLLECTIONS_LAST_OR_NULL, MAX),
        Conversion(KOTLIN_COLLECTIONS_SORTED_DESCENDING, KOTLIN_COLLECTIONS_FIRST_OR_NULL, MAX),
        Conversion(KOTLIN_COLLECTIONS_SORTED_DESCENDING, KOTLIN_COLLECTIONS_LAST_OR_NULL, MIN),
        Conversion(KOTLIN_COLLECTIONS_SORTED_BY, KOTLIN_COLLECTIONS_FIRST_OR_NULL, MIN_BY),
        Conversion(KOTLIN_COLLECTIONS_SORTED_BY, KOTLIN_COLLECTIONS_LAST_OR_NULL, MAX_BY),
        Conversion(KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING, KOTLIN_COLLECTIONS_FIRST_OR_NULL, MAX_BY),
        Conversion(KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING, KOTLIN_COLLECTIONS_LAST_OR_NULL, MIN_BY),
        Conversion(KOTLIN_COLLECTIONS_SORTED, KOTLIN_COLLECTIONS_FIRST, MIN, addNotNullAssertion = true),
        Conversion(KOTLIN_COLLECTIONS_SORTED, KOTLIN_COLLECTIONS_LAST, MAX, addNotNullAssertion = true),
        Conversion(KOTLIN_COLLECTIONS_SORTED_DESCENDING, KOTLIN_COLLECTIONS_FIRST, MAX, addNotNullAssertion = true),
        Conversion(KOTLIN_COLLECTIONS_SORTED_DESCENDING, KOTLIN_COLLECTIONS_LAST, MIN, addNotNullAssertion = true),
        Conversion(KOTLIN_COLLECTIONS_SORTED_BY, KOTLIN_COLLECTIONS_FIRST, MIN_BY, addNotNullAssertion = true),
        Conversion(KOTLIN_COLLECTIONS_SORTED_BY, KOTLIN_COLLECTIONS_LAST, MAX_BY, addNotNullAssertion = true),
        Conversion(KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING, KOTLIN_COLLECTIONS_FIRST, MAX_BY, addNotNullAssertion = true),
        Conversion(KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING, KOTLIN_COLLECTIONS_LAST, MIN_BY, addNotNullAssertion = true),

        Conversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_FIRST, FIRST),
        Conversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_FIRST_OR_NULL, FIRST_OR_NULL),
        Conversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_LAST, LAST),
        Conversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_LAST_OR_NULL, LAST_OR_NULL),
        Conversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_SINGLE, SINGLE),
        Conversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_SINGLE_OR_NULL, SINGLE_OR_NULL),
        Conversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_IS_NOT_EMPTY, ANY),
        Conversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_IS_EMPTY, NONE),
        Conversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_COUNT, COUNT),
        Conversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_ANY, ANY),
        Conversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_NONE, NONE),

        Conversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_JOIN_TO, JOIN_TO, enableSuspendFunctionCall = false),
        Conversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_JOIN_TO_STRING, JOIN_TO_STRING, enableSuspendFunctionCall = false),
        Conversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_FILTER_NOT_NULL, MAP_NOT_NULL),
        Conversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_TO_MAP, ASSOCIATE),
        Conversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_TO_MAP, ASSOCIATE_TO),
        Conversion(
            KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_SUM, SUM_OF, replaceableApiVersion = ApiVersion.KOTLIN_1_4
        ),
        Conversion(
            KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MAX, MAX_OF,
            removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
        ),
        Conversion(
            KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MAX, MAX_OF_OR_NULL,
            replaceableApiVersion = ApiVersion.KOTLIN_1_4,
        ),
        Conversion(
            KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MAX_OR_NULL, MAX_OF,
            removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
        ),
        Conversion(
            KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MAX_OR_NULL, MAX_OF_OR_NULL,
            replaceableApiVersion = ApiVersion.KOTLIN_1_4,
        ),
        Conversion(
            KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MIN, MIN_OF,
            removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
        ),
        Conversion(
            KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MIN, MIN_OF_OR_NULL,
            replaceableApiVersion = ApiVersion.KOTLIN_1_4,
        ),
        Conversion(
            KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MIN_OR_NULL, MIN_OF,
            removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
        ),
        Conversion(
            KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MIN_OR_NULL, MIN_OF_OR_NULL,
            replaceableApiVersion = ApiVersion.KOTLIN_1_4,
        ),
        Conversion(
            KOTLIN_COLLECTIONS_MAP_NOT_NULL, KOTLIN_COLLECTIONS_FIRST, FIRST_NOT_NULL_OF,
            replaceableApiVersion = ApiVersion.KOTLIN_1_5
        ),
        Conversion(
            KOTLIN_COLLECTIONS_MAP_NOT_NULL, KOTLIN_COLLECTIONS_FIRST_OR_NULL, FIRST_NOT_NULL_OF_OR_NULL,
            replaceableApiVersion = ApiVersion.KOTLIN_1_5
        ),
        Conversion(KOTLIN_COLLECTIONS_LIST_OF, KOTLIN_COLLECTIONS_FILTER_NOT_NULL, LIST_OF_NOT_NULL)
    ).map {
        when (val replacement = it.replacement) {
            MIN, MAX, MIN_BY, MAX_BY -> {
                val additionalConversion = if ((replacement == MIN || replacement == MAX) && it.addNotNullAssertion) {
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
}
