// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunction.*
import org.jetbrains.kotlin.idea.refactoring.getLastLambdaExpression
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis

data class CallChainConversion(
    val firstFqName: FqName,
    val secondFqName: FqName,
    @NonNls val replacement: String,
    @NonNls val additionalArgument: String? = null,
    val addNotNullAssertion: Boolean = false,
    val enableSuspendFunctionCall: Boolean = true,
    val removeNotNullAssertion: Boolean = false,
    val replaceableApiVersion: ApiVersion? = null,
) {
    val id: ConversionId get() = ConversionId(firstName, secondName)

    val firstName: String = firstFqName.shortName().asString()
    val secondName: String = secondFqName.shortName().asString()

    fun withArgument(argument: String): CallChainConversion = CallChainConversion(firstFqName, secondFqName, replacement, argument)
}

class CallChainExpressions private constructor(
    val firstExpression: KtExpression,
    val firstCallExpression: KtCallExpression,
    val secondCallExpression: KtCallExpression,
    val firstCalleeExpression: KtExpression,
    val secondCalleeExpression: KtExpression,
    val qualifiedExpression: KtQualifiedExpression,
) {
    // it's the same as the whole qualified expression, but it makes it more readable in some places
    val secondExpression: KtExpression
        get() = qualifiedExpression

    companion object {
        fun from(expression: KtQualifiedExpression): CallChainExpressions? {
            val firstExpression = expression.receiverExpression
            val firstCallExpression = getFirstCallExpression(firstExpression) ?: return null
            val secondCallExpression = expression.selectorExpression as? KtCallExpression ?: return null
            val firstCalleeExpression = firstCallExpression.calleeExpression ?: return null
            val secondCalleeExpression = secondCallExpression.calleeExpression ?: return null
            return CallChainExpressions(
                firstExpression = firstExpression,
                firstCallExpression = firstCallExpression,
                secondCallExpression = secondCallExpression,
                firstCalleeExpression = firstCalleeExpression,
                secondCalleeExpression = secondCalleeExpression,
                qualifiedExpression = expression,
            )
        }

        fun getFirstCallExpression(firstExpression: KtExpression): KtCallExpression? =
            (firstExpression as? KtQualifiedExpression)?.selectorExpression as? KtCallExpression
                ?: firstExpression as? KtCallExpression

        fun KtExpression?.isLiteralValue(): Boolean {
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
    }
}

data class ConversionId(val firstFqName: String, val secondFqName: String) {
    constructor(first: KtExpression, second: KtExpression) : this(first.text, second.text)
}

@Suppress("MemberVisibilityCanBePrivate")
object CallChainConversions {
    private val KOTLIN_COLLECTIONS_ANY = FqName("kotlin.collections.any")
    private val KOTLIN_COLLECTIONS_COUNT = FqName("kotlin.collections.count")
    private val KOTLIN_COLLECTIONS_FILTER = FqName("kotlin.collections.filter")
    private val KOTLIN_COLLECTIONS_FILTER_NOT_NULL = FqName("kotlin.collections.filterNotNull")
    private val KOTLIN_COLLECTIONS_FIRST = FqName("kotlin.collections.first")
    private val KOTLIN_COLLECTIONS_FIRST_OR_NULL = FqName("kotlin.collections.firstOrNull")
    private val KOTLIN_COLLECTIONS_IS_NOT_EMPTY = FqName("kotlin.collections.isNotEmpty")
    private val KOTLIN_COLLECTIONS_JOIN_TO = FqName("kotlin.collections.joinTo")
    private val KOTLIN_COLLECTIONS_JOIN_TO_STRING = FqName("kotlin.collections.joinToString")
    private val KOTLIN_COLLECTIONS_LAST = FqName("kotlin.collections.last")
    private val KOTLIN_COLLECTIONS_LAST_OR_NULL = FqName("kotlin.collections.lastOrNull")
    private val KOTLIN_COLLECTIONS_LIST_IS_EMPTY = FqName("kotlin.collections.List.isEmpty")
    private val KOTLIN_COLLECTIONS_LIST_OF = FqName("kotlin.collections.listOf")
    private val KOTLIN_COLLECTIONS_MAP = FqName("kotlin.collections.map")
    private val KOTLIN_COLLECTIONS_MAP_NOT_NULL = FqName("kotlin.collections.mapNotNull")
    private val KOTLIN_COLLECTIONS_MAX = FqName("kotlin.collections.max")
    private val KOTLIN_COLLECTIONS_MAX_OR_NULL = FqName("kotlin.collections.maxOrNull")
    private val KOTLIN_COLLECTIONS_MIN = FqName("kotlin.collections.min")
    private val KOTLIN_COLLECTIONS_MIN_OR_NULL = FqName("kotlin.collections.minOrNull")
    private val KOTLIN_COLLECTIONS_NONE = FqName("kotlin.collections.none")
    private val KOTLIN_COLLECTIONS_SINGLE = FqName("kotlin.collections.single")
    private val KOTLIN_COLLECTIONS_SINGLE_OR_NULL = FqName("kotlin.collections.singleOrNull")
    private val KOTLIN_COLLECTIONS_SORTED = FqName("kotlin.collections.sorted")
    private val KOTLIN_COLLECTIONS_SORTED_BY = FqName("kotlin.collections.sortedBy")
    private val KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING = FqName("kotlin.collections.sortedByDescending")
    private val KOTLIN_COLLECTIONS_SORTED_DESCENDING = FqName("kotlin.collections.sortedDescending")
    private val KOTLIN_COLLECTIONS_SUM = FqName("kotlin.collections.sum")
    private val KOTLIN_COLLECTIONS_TO_MAP = FqName("kotlin.collections.toMap")
    private val KOTLIN_TEXT_ANY = FqName("kotlin.text.any")
    private val KOTLIN_TEXT_COUNT = FqName("kotlin.text.count")
    private val KOTLIN_TEXT_FILTER = FqName("kotlin.text.filter")
    private val KOTLIN_TEXT_FIRST = FqName("kotlin.text.first")
    private val KOTLIN_TEXT_FIRST_OR_NULL = FqName("kotlin.text.firstOrNull")
    private val KOTLIN_TEXT_IS_EMPTY = FqName("kotlin.text.isEmpty")
    private val KOTLIN_TEXT_IS_NOT_EMPTY = FqName("kotlin.text.isNotEmpty")
    private val KOTLIN_TEXT_LAST = FqName("kotlin.text.last")
    private val KOTLIN_TEXT_LAST_OR_NULL = FqName("kotlin.text.lastOrNull")
    private val KOTLIN_TEXT_NONE = FqName("kotlin.text.none")
    private val KOTLIN_TEXT_SINGLE = FqName("kotlin.text.single")
    private val KOTLIN_TEXT_SINGLE_OR_NULL = FqName("kotlin.text.singleOrNull")

    // replacements
    const val FIRST: String = "first"
    const val FIRST_OR_NULL: String = "firstOrNull"
    const val LAST: String = "last"
    const val LAST_OR_NULL: String = "lastOrNull"
    const val SINGLE: String = "single"
    const val SINGLE_OR_NULL: String = "singleOrNull"
    const val ANY: String = "any"
    const val NONE: String = "none"
    const val COUNT: String = "count"
    const val MIN: String = "min"
    const val MAX: String = "max"
    const val MIN_OR_NULL: String = "minOrNull"
    const val MAX_OR_NULL: String = "maxOrNull"
    const val MIN_BY: String = "minBy"
    const val MAX_BY: String = "maxBy"
    const val MIN_BY_OR_NULL: String = "minByOrNull"
    const val MAX_BY_OR_NULL: String = "maxByOrNull"
    const val JOIN_TO: String = "joinTo"
    const val JOIN_TO_STRING: String = "joinToString"
    const val MAP_NOT_NULL: String = "mapNotNull"
    const val ASSOCIATE: String = "associate"
    const val ASSOCIATE_TO: String = "associateTo"
    const val SUM_OF: String = "sumOf"
    const val MAX_OF: String = "maxOf"
    const val MAX_OF_OR_NULL: String = "maxOfOrNull"
    const val MIN_OF: String = "minOf"
    const val MIN_OF_OR_NULL: String = "minOfOrNull"
    const val FIRST_NOT_NULL_OF: String = "firstNotNullOf"
    const val FIRST_NOT_NULL_OF_OR_NULL: String = "firstNotNullOfOrNull"
    const val LIST_OF_NOT_NULL: String = "listOfNotNull"
    const val MAP: String = "map"
    const val SUM: String = "sum"
    const val TO_MAP: String = "toMap"

    val conversionsList: List<CallChainConversion> by lazy {
        listOf(
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_FIRST, FIRST),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_FIRST_OR_NULL, FIRST_OR_NULL),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_LAST, LAST),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_LAST_OR_NULL, LAST_OR_NULL),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_SINGLE, SINGLE),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_SINGLE_OR_NULL, SINGLE_OR_NULL),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_IS_NOT_EMPTY, ANY),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_LIST_IS_EMPTY, NONE),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_COUNT, COUNT),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_ANY, ANY),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_NONE, NONE),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED, KOTLIN_COLLECTIONS_FIRST_OR_NULL, MIN),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED, KOTLIN_COLLECTIONS_LAST_OR_NULL, MAX),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_DESCENDING, KOTLIN_COLLECTIONS_FIRST_OR_NULL, MAX),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_DESCENDING, KOTLIN_COLLECTIONS_LAST_OR_NULL, MIN),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY, KOTLIN_COLLECTIONS_FIRST_OR_NULL, MIN_BY),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY, KOTLIN_COLLECTIONS_LAST_OR_NULL, MAX_BY),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING, KOTLIN_COLLECTIONS_FIRST_OR_NULL, MAX_BY),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING, KOTLIN_COLLECTIONS_LAST_OR_NULL, MIN_BY),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED, KOTLIN_COLLECTIONS_FIRST, MIN, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED, KOTLIN_COLLECTIONS_LAST, MAX, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_DESCENDING, KOTLIN_COLLECTIONS_FIRST, MAX, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_DESCENDING, KOTLIN_COLLECTIONS_LAST, MIN, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY, KOTLIN_COLLECTIONS_FIRST, MIN_BY, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY, KOTLIN_COLLECTIONS_LAST, MAX_BY, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING, KOTLIN_COLLECTIONS_FIRST, MAX_BY, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING, KOTLIN_COLLECTIONS_LAST, MIN_BY, addNotNullAssertion = true),

            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_FIRST, FIRST),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_FIRST_OR_NULL, FIRST_OR_NULL),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_LAST, LAST),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_LAST_OR_NULL, LAST_OR_NULL),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_SINGLE, SINGLE),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_SINGLE_OR_NULL, SINGLE_OR_NULL),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_IS_NOT_EMPTY, ANY),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_IS_EMPTY, NONE),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_COUNT, COUNT),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_ANY, ANY),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_NONE, NONE),

            CallChainConversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_JOIN_TO, JOIN_TO, enableSuspendFunctionCall = false),
            CallChainConversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_JOIN_TO_STRING, JOIN_TO_STRING, enableSuspendFunctionCall = false),
            CallChainConversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_FILTER_NOT_NULL, MAP_NOT_NULL),
            CallChainConversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_TO_MAP, ASSOCIATE),
            CallChainConversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_TO_MAP, ASSOCIATE_TO),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_SUM, SUM_OF, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MAX, MAX_OF,
                removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MAX, MAX_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_4,
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MAX_OR_NULL, MAX_OF,
                removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MAX_OR_NULL, MAX_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_4,
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MIN, MIN_OF,
                removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MIN, MIN_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_4,
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MIN_OR_NULL, MIN_OF,
                removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MIN_OR_NULL, MIN_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_4,
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP_NOT_NULL, KOTLIN_COLLECTIONS_FIRST, FIRST_NOT_NULL_OF,
                replaceableApiVersion = ApiVersion.KOTLIN_1_5
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP_NOT_NULL, KOTLIN_COLLECTIONS_FIRST_OR_NULL, FIRST_NOT_NULL_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_5
            ),
            CallChainConversion(KOTLIN_COLLECTIONS_LIST_OF, KOTLIN_COLLECTIONS_FILTER_NOT_NULL, LIST_OF_NOT_NULL)
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

    val conversionGroups: Map<ConversionId, List<CallChainConversion>> by lazy {
        conversionsList.groupBy { conversion -> conversion.id }
    }
}

enum class AssociateFunction(val functionName: String) {
    ASSOCIATE_WITH("associateWith"),
    ASSOCIATE_BY("associateBy"),
    ASSOCIATE_BY_KEY_AND_VALUE("associateBy");

    fun name(hasDestination: Boolean): String =
        if (hasDestination) "${functionName}To" else functionName
}

@ApiStatus.Internal
object AssociateFunctionUtil {
    @ApiStatus.Internal
    fun KtCallExpression.lambda(): KtLambdaExpression? {
        return lambdaArguments.singleOrNull()?.getArgumentExpression() as? KtLambdaExpression ?: getLastLambdaExpression()
    }

    @ApiStatus.Internal
    fun KtFunctionLiteral.lastStatement(): KtExpression? {
        return bodyExpression?.statements?.lastOrNull()
    }

    @ApiStatus.Internal
    fun getAssociateFunctionAndProblemHighlightType(
        dotQualifiedExpression: KtDotQualifiedExpression,
    ): Pair<AssociateFunction, ProblemHighlightType>? {
        val callExpression = dotQualifiedExpression.callExpression ?: return null
        val lambda = callExpression.lambda() ?: return null
        if (lambda.valueParameters.size > 1) return null
        val functionLiteral = lambda.functionLiteral
        if (functionLiteral.anyDescendantOfType<KtReturnExpression> { it.labelQualifier != null }) return null
        val lastStatement = functionLiteral.lastStatement() ?: return null
        analyze(dotQualifiedExpression) {
            val (keySelector, valueTransform) = pair(lastStatement) ?: return null
            val lambdaParameter: KaValueParameterSymbol = functionLiteral.symbol.valueParameters.singleOrNull() ?: return null
            return when {
                keySelector.isReferenceTo(lambdaParameter) ->
                    ASSOCIATE_WITH to GENERIC_ERROR_OR_WARNING

                valueTransform.isReferenceTo(lambdaParameter) ->
                    ASSOCIATE_BY to GENERIC_ERROR_OR_WARNING

                else -> {
                    if (functionLiteral.bodyExpression?.statements?.size != 1) return null
                    ASSOCIATE_BY_KEY_AND_VALUE to INFORMATION
                }
            }
        }
    }

    context(KaSession)
    private fun KtExpression.isReferenceTo(another: KaValueParameterSymbol): Boolean {
        val referenceExpression = this as? KtNameReferenceExpression ?: return false
        val symbol = referenceExpression.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()?.symbol
        return symbol == another
    }

    @ApiStatus.Internal
    fun KaSession.pair(expression: KtExpression): Pair<KtExpression, KtExpression>? {
        return with(expression) {
            when (this) {
                is KtBinaryExpression -> {
                    if (operationReference.text != "to") return null
                    val left = left ?: return null
                    val right = right ?: return null
                    left to right
                }
                is KtCallExpression -> {
                    if (calleeExpression?.text != "Pair") return null
                    if (valueArguments.size != 2) return null
                    val constructorSymbol = resolveToCall()?.singleConstructorCallOrNull()?.symbol ?: return null
                    val classId = (constructorSymbol.returnType as? KaClassType)?.classId ?: return null
                    if (classId != PAIR_CLASS_ID) return null
                    val first = valueArguments[0]?.getArgumentExpression() ?: return null
                    val second = valueArguments[1]?.getArgumentExpression() ?: return null
                    first to second
                }
                else -> return null
            }
        }
    }
}

private val PAIR_CLASS_ID =
    ClassId(StandardNames.BUILT_INS_PACKAGE_FQ_NAME, Name.identifier("Pair"))
