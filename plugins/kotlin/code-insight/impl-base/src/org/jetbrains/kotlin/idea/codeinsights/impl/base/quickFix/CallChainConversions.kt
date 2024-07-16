// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
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

    val firstName = firstFqName.shortName().asString()
    val secondName = secondFqName.shortName().asString()

    fun withArgument(argument: String) = CallChainConversion(firstFqName, secondFqName, replacement, argument)
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
    const val TO_MAP = "toMap"

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
