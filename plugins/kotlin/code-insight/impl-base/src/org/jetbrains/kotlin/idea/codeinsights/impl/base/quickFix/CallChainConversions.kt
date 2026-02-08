// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunction.ASSOCIATE_BY
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunction.ASSOCIATE_BY_KEY_AND_VALUE
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunction.ASSOCIATE_WITH
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.collectionsOperationsConversions
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.sequencesOperationsConversions
import org.jetbrains.kotlin.idea.refactoring.getLastLambdaExpression
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis

data class CallChainConversion(
    val firstFqName: FqName,
    val secondFqName: FqName,
    val replacementFqName: FqName,
    @NonNls val additionalArgument: String? = null,
    val addNotNullAssertion: Boolean = false,
    val enableSuspendFunctionCall: Boolean = true,
    val removeNotNullAssertion: Boolean = false,
    val replaceableApiVersion: ApiVersion? = null,
) {
    val id: ConversionId get() = ConversionId(firstName, secondName)

    val firstName: String = firstFqName.shortName().asString()
    val secondName: String = secondFqName.shortName().asString()
    @NonNls val replacementName: String = replacementFqName.shortName().asString()

    fun withArgument(argument: String): CallChainConversion = CallChainConversion(firstFqName, secondFqName, replacementFqName, argument)
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
    // collections
    private val KOTLIN_COLLECTIONS_ANY = FqName("kotlin.collections.any")
    private val KOTLIN_COLLECTIONS_ASSOCIATE = FqName("kotlin.collections.associate")
    private val KOTLIN_COLLECTIONS_ASSOCIATE_TO = FqName("kotlin.collections.associateTo")
    private val KOTLIN_COLLECTIONS_COUNT = FqName("kotlin.collections.count")
    private val KOTLIN_COLLECTIONS_FILTER = FqName("kotlin.collections.filter")
    private val KOTLIN_COLLECTIONS_FILTER_NOT_NULL = FqName("kotlin.collections.filterNotNull")
    private val KOTLIN_COLLECTIONS_FIRST = FqName("kotlin.collections.first")
    private val KOTLIN_COLLECTIONS_FIRST_NOT_NULL_OF = FqName("kotlin.collections.firstNotNullOf")
    private val KOTLIN_COLLECTIONS_FIRST_NOT_NULL_OF_OR_NULL = FqName("kotlin.collections.firstNotNullOfOrNull")
    private val KOTLIN_COLLECTIONS_FIRST_OR_NULL = FqName("kotlin.collections.firstOrNull")
    private val KOTLIN_COLLECTIONS_FLATTEN = FqName("kotlin.collections.flatten")
    private val KOTLIN_COLLECTIONS_FLAT_MAP = FqName("kotlin.collections.flatMap")
    private val KOTLIN_COLLECTIONS_FLAT_MAP_INDEXED = FqName("kotlin.collections.flatMapIndexed")
    private val KOTLIN_COLLECTIONS_IS_NOT_EMPTY = FqName("kotlin.collections.isNotEmpty")
    private val KOTLIN_COLLECTIONS_JOIN_TO = FqName("kotlin.collections.joinTo")
    private val KOTLIN_COLLECTIONS_JOIN_TO_STRING = FqName("kotlin.collections.joinToString")
    private val KOTLIN_COLLECTIONS_LAST = FqName("kotlin.collections.last")
    private val KOTLIN_COLLECTIONS_LAST_OR_NULL = FqName("kotlin.collections.lastOrNull")
    private val KOTLIN_COLLECTIONS_LIST_IS_EMPTY = FqName("kotlin.collections.List.isEmpty")
    private val KOTLIN_COLLECTIONS_LIST_OF = FqName("kotlin.collections.listOf")
    private val KOTLIN_COLLECTIONS_LIST_OF_NOT_NULL = FqName("kotlin.collections.listOfNotNull")
    private val KOTLIN_COLLECTIONS_MAP = FqName("kotlin.collections.map")
    private val KOTLIN_COLLECTIONS_MAP_INDEXED = FqName("kotlin.collections.mapIndexed")
    private val KOTLIN_COLLECTIONS_MAP_NOT_NULL = FqName("kotlin.collections.mapNotNull")
    private val KOTLIN_COLLECTIONS_MAX = FqName("kotlin.collections.max")
    private val KOTLIN_COLLECTIONS_MAX_BY = FqName("kotlin.collections.maxBy")
    private val KOTLIN_COLLECTIONS_MAX_OF = FqName("kotlin.collections.maxOf")
    private val KOTLIN_COLLECTIONS_MAX_OF_OR_NULL = FqName("kotlin.collections.maxOfOrNull")
    private val KOTLIN_COLLECTIONS_MAX_OR_NULL = FqName("kotlin.collections.maxOrNull")
    private val KOTLIN_COLLECTIONS_MIN = FqName("kotlin.collections.min")
    private val KOTLIN_COLLECTIONS_MIN_BY = FqName("kotlin.collections.minBy")
    private val KOTLIN_COLLECTIONS_MIN_OF = FqName("kotlin.collections.minOf")
    private val KOTLIN_COLLECTIONS_MIN_OF_OR_NULL = FqName("kotlin.collections.minOfOrNull")
    private val KOTLIN_COLLECTIONS_MIN_OR_NULL = FqName("kotlin.collections.minOrNull")
    private val KOTLIN_COLLECTIONS_NONE = FqName("kotlin.collections.none")
    private val KOTLIN_COLLECTIONS_SINGLE = FqName("kotlin.collections.single")
    private val KOTLIN_COLLECTIONS_SINGLE_OR_NULL = FqName("kotlin.collections.singleOrNull")
    private val KOTLIN_COLLECTIONS_SORTED = FqName("kotlin.collections.sorted")
    private val KOTLIN_COLLECTIONS_SORTED_BY = FqName("kotlin.collections.sortedBy")
    private val KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING = FqName("kotlin.collections.sortedByDescending")
    private val KOTLIN_COLLECTIONS_SORTED_DESCENDING = FqName("kotlin.collections.sortedDescending")
    private val KOTLIN_COLLECTIONS_SUM = FqName("kotlin.collections.sum")
    private val KOTLIN_COLLECTIONS_SUM_OF = FqName("kotlin.collections.sumOf")
    private val KOTLIN_COLLECTIONS_TO_MAP = FqName("kotlin.collections.toMap")

    // sequences
    private val KOTLIN_SEQUENCES_ANY = FqName("kotlin.sequences.any")
    private val KOTLIN_SEQUENCES_ASSOCIATE = FqName("kotlin.sequences.associate")
    private val KOTLIN_SEQUENCES_ASSOCIATE_TO = FqName("kotlin.sequences.associateTo")
    private val KOTLIN_SEQUENCES_COUNT = FqName("kotlin.sequences.count")
    private val KOTLIN_SEQUENCES_FILTER = FqName("kotlin.sequences.filter")
    private val KOTLIN_SEQUENCES_FILTER_NOT_NULL = FqName("kotlin.sequences.filterNotNull")
    private val KOTLIN_SEQUENCES_FIRST = FqName("kotlin.sequences.first")
    private val KOTLIN_SEQUENCES_FIRST_NOT_NULL_OF = FqName("kotlin.sequences.firstNotNullOf")
    private val KOTLIN_SEQUENCES_FIRST_NOT_NULL_OF_OR_NULL = FqName("kotlin.sequences.firstNotNullOfOrNull")
    private val KOTLIN_SEQUENCES_FIRST_OR_NULL = FqName("kotlin.sequences.firstOrNull")
    private val KOTLIN_SEQUENCES_FLATTEN = FqName("kotlin.sequences.flatten")
    private val KOTLIN_SEQUENCES_FLAT_MAP = FqName("kotlin.sequences.flatMap")
    private val KOTLIN_SEQUENCES_FLAT_MAP_INDEXED = FqName("kotlin.sequences.flatMapIndexed")
    private val KOTLIN_SEQUENCES_JOIN_TO = FqName("kotlin.sequences.joinTo")
    private val KOTLIN_SEQUENCES_JOIN_TO_STRING = FqName("kotlin.sequences.joinToString")
    private val KOTLIN_SEQUENCES_LAST = FqName("kotlin.sequences.last")
    private val KOTLIN_SEQUENCES_LAST_OR_NULL = FqName("kotlin.sequences.lastOrNull")
    private val KOTLIN_SEQUENCES_MAP = FqName("kotlin.sequences.map")
    private val KOTLIN_SEQUENCES_MAP_INDEXED = FqName("kotlin.sequences.mapIndexed")
    private val KOTLIN_SEQUENCES_MAP_NOT_NULL = FqName("kotlin.sequences.mapNotNull")
    private val KOTLIN_SEQUENCES_MAX = FqName("kotlin.sequences.max")
    private val KOTLIN_SEQUENCES_MAX_BY = FqName("kotlin.sequences.maxBy")
    private val KOTLIN_SEQUENCES_MAX_OF = FqName("kotlin.sequences.maxOf")
    private val KOTLIN_SEQUENCES_MAX_OF_OR_NULL = FqName("kotlin.sequences.maxOfOrNull")
    private val KOTLIN_SEQUENCES_MAX_OR_NULL = FqName("kotlin.sequences.maxOrNull")
    private val KOTLIN_SEQUENCES_MIN = FqName("kotlin.sequences.min")
    private val KOTLIN_SEQUENCES_MIN_BY = FqName("kotlin.sequences.minBy")
    private val KOTLIN_SEQUENCES_MIN_OF = FqName("kotlin.sequences.minOf")
    private val KOTLIN_SEQUENCES_MIN_OF_OR_NULL = FqName("kotlin.sequences.minOfOrNull")
    private val KOTLIN_SEQUENCES_MIN_OR_NULL = FqName("kotlin.sequences.minOrNull")
    private val KOTLIN_SEQUENCES_NONE = FqName("kotlin.sequences.none")
    private val KOTLIN_SEQUENCES_SINGLE = FqName("kotlin.sequences.single")
    private val KOTLIN_SEQUENCES_SINGLE_OR_NULL = FqName("kotlin.sequences.singleOrNull")
    private val KOTLIN_SEQUENCES_SORTED = FqName("kotlin.sequences.sorted")
    private val KOTLIN_SEQUENCES_SORTED_BY = FqName("kotlin.sequences.sortedBy")
    private val KOTLIN_SEQUENCES_SORTED_BY_DESCENDING = FqName("kotlin.sequences.sortedByDescending")
    private val KOTLIN_SEQUENCES_SORTED_DESCENDING = FqName("kotlin.sequences.sortedDescending")
    private val KOTLIN_SEQUENCES_SUM = FqName("kotlin.sequences.sum")
    private val KOTLIN_SEQUENCES_SUM_OF = FqName("kotlin.sequences.sumOf")

    // text
    private val KOTLIN_TEXT_ANY = FqName("kotlin.text.any")
    private val KOTLIN_TEXT_COUNT = FqName("kotlin.text.count")
    private val KOTLIN_TEXT_FILTER = FqName("kotlin.text.filter")
    private val KOTLIN_TEXT_FLAT_MAP = FqName("kotlin.text.flatMap")
    private val KOTLIN_TEXT_FLAT_MAP_INDEXED = FqName("kotlin.text.flatMapIndexed")
    private val KOTLIN_TEXT_FIRST = FqName("kotlin.text.first")
    private val KOTLIN_TEXT_FIRST_OR_NULL = FqName("kotlin.text.firstOrNull")
    private val KOTLIN_TEXT_IS_EMPTY = FqName("kotlin.text.isEmpty")
    private val KOTLIN_TEXT_IS_NOT_EMPTY = FqName("kotlin.text.isNotEmpty")
    private val KOTLIN_TEXT_LAST = FqName("kotlin.text.last")
    private val KOTLIN_TEXT_LAST_OR_NULL = FqName("kotlin.text.lastOrNull")
    private val KOTLIN_TEXT_MAP = FqName("kotlin.text.map")
    private val KOTLIN_TEXT_MAP_INDEXED = FqName("kotlin.text.mapIndexed")
    private val KOTLIN_TEXT_NONE = FqName("kotlin.text.none")
    private val KOTLIN_TEXT_SINGLE = FqName("kotlin.text.single")
    private val KOTLIN_TEXT_SINGLE_OR_NULL = FqName("kotlin.text.singleOrNull")

    // replacements
    const val MIN: String = "min"
    const val MAX: String = "max"
    const val MIN_OR_NULL: String = "minOrNull"
    const val MAX_OR_NULL: String = "maxOrNull"
    const val MIN_BY: String = "minBy"
    const val MAX_BY: String = "maxBy"
    const val MIN_BY_OR_NULL: String = "minByOrNull"
    const val MAX_BY_OR_NULL: String = "maxByOrNull"
    const val JOIN_TO: String = "joinTo"
    const val MAP_NOT_NULL: String = "mapNotNull"
    const val ASSOCIATE: String = "associate"
    const val ASSOCIATE_TO: String = "associateTo"
    const val SUM_OF: String = "sumOf"
    const val MAP: String = "map"
    const val SUM: String = "sum"
    const val TO_MAP: String = "toMap"

    /**
     * N.B. This list should closely mirror [sequencesOperationsConversions].
     * If you add a new conversion for collections, consider adding it for sequences if applicable.
     */
    private val collectionsOperationsConversions: List<CallChainConversion> by lazy {
        listOf(
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_FIRST, KOTLIN_COLLECTIONS_FIRST),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_FIRST_OR_NULL, KOTLIN_COLLECTIONS_FIRST_OR_NULL),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_LAST, KOTLIN_COLLECTIONS_LAST),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_LAST_OR_NULL, KOTLIN_COLLECTIONS_LAST_OR_NULL),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_SINGLE, KOTLIN_COLLECTIONS_SINGLE),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_SINGLE_OR_NULL, KOTLIN_COLLECTIONS_SINGLE_OR_NULL),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_IS_NOT_EMPTY, KOTLIN_COLLECTIONS_ANY),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_LIST_IS_EMPTY, KOTLIN_COLLECTIONS_NONE),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_COUNT, KOTLIN_COLLECTIONS_COUNT),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_ANY, KOTLIN_COLLECTIONS_ANY),
            CallChainConversion(KOTLIN_COLLECTIONS_FILTER, KOTLIN_COLLECTIONS_NONE, KOTLIN_COLLECTIONS_NONE),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED, KOTLIN_COLLECTIONS_FIRST_OR_NULL, KOTLIN_COLLECTIONS_MIN),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED, KOTLIN_COLLECTIONS_LAST_OR_NULL, KOTLIN_COLLECTIONS_MAX),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_DESCENDING, KOTLIN_COLLECTIONS_FIRST_OR_NULL, KOTLIN_COLLECTIONS_MAX),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_DESCENDING, KOTLIN_COLLECTIONS_LAST_OR_NULL, KOTLIN_COLLECTIONS_MIN),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY, KOTLIN_COLLECTIONS_FIRST_OR_NULL, KOTLIN_COLLECTIONS_MIN_BY),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY, KOTLIN_COLLECTIONS_LAST_OR_NULL, KOTLIN_COLLECTIONS_MAX_BY),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING, KOTLIN_COLLECTIONS_FIRST_OR_NULL, KOTLIN_COLLECTIONS_MAX_BY),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING, KOTLIN_COLLECTIONS_LAST_OR_NULL, KOTLIN_COLLECTIONS_MIN_BY),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED, KOTLIN_COLLECTIONS_FIRST, KOTLIN_COLLECTIONS_MIN, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED, KOTLIN_COLLECTIONS_LAST, KOTLIN_COLLECTIONS_MAX, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_DESCENDING, KOTLIN_COLLECTIONS_FIRST, KOTLIN_COLLECTIONS_MAX, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_DESCENDING, KOTLIN_COLLECTIONS_LAST, KOTLIN_COLLECTIONS_MIN, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY, KOTLIN_COLLECTIONS_FIRST, KOTLIN_COLLECTIONS_MIN_BY, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY, KOTLIN_COLLECTIONS_LAST, KOTLIN_COLLECTIONS_MAX_BY, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING, KOTLIN_COLLECTIONS_FIRST, KOTLIN_COLLECTIONS_MAX_BY, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_COLLECTIONS_SORTED_BY_DESCENDING, KOTLIN_COLLECTIONS_LAST, KOTLIN_COLLECTIONS_MIN_BY, addNotNullAssertion = true),

            CallChainConversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_JOIN_TO, KOTLIN_COLLECTIONS_JOIN_TO, enableSuspendFunctionCall = false),
            CallChainConversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_JOIN_TO_STRING, KOTLIN_COLLECTIONS_JOIN_TO_STRING, enableSuspendFunctionCall = false),
            CallChainConversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_FILTER_NOT_NULL, KOTLIN_COLLECTIONS_MAP_NOT_NULL),
            CallChainConversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_TO_MAP, KOTLIN_COLLECTIONS_ASSOCIATE),
            CallChainConversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_TO_MAP, KOTLIN_COLLECTIONS_ASSOCIATE_TO),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_SUM, KOTLIN_COLLECTIONS_SUM_OF, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MAX, KOTLIN_COLLECTIONS_MAX_OF,
                removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MAX, KOTLIN_COLLECTIONS_MAX_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_4,
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MAX_OR_NULL, KOTLIN_COLLECTIONS_MAX_OF,
                removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MAX_OR_NULL, KOTLIN_COLLECTIONS_MAX_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_4,
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MIN, KOTLIN_COLLECTIONS_MIN_OF,
                removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MIN, KOTLIN_COLLECTIONS_MIN_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_4,
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MIN_OR_NULL, KOTLIN_COLLECTIONS_MIN_OF,
                removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_MIN_OR_NULL, KOTLIN_COLLECTIONS_MIN_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_4,
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP_NOT_NULL, KOTLIN_COLLECTIONS_FIRST, KOTLIN_COLLECTIONS_FIRST_NOT_NULL_OF,
                replaceableApiVersion = ApiVersion.KOTLIN_1_5
            ),
            CallChainConversion(
                KOTLIN_COLLECTIONS_MAP_NOT_NULL, KOTLIN_COLLECTIONS_FIRST_OR_NULL, KOTLIN_COLLECTIONS_FIRST_NOT_NULL_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_5
            ),

            CallChainConversion(KOTLIN_COLLECTIONS_MAP, KOTLIN_COLLECTIONS_FLATTEN, KOTLIN_COLLECTIONS_FLAT_MAP),
            CallChainConversion(KOTLIN_COLLECTIONS_MAP_INDEXED, KOTLIN_COLLECTIONS_FLATTEN, KOTLIN_COLLECTIONS_FLAT_MAP_INDEXED),
        ).flatMap {
            it.withAdditionalMinMaxConversions()
        }
    }

    /**
     * N.B. This list should closely mirror [collectionsOperationsConversions].
     * If you add a new conversion for sequences, consider adding it for collections if applicable.
     */
    private val sequencesOperationsConversions: List<CallChainConversion> by lazy {
        listOf(
            CallChainConversion(KOTLIN_SEQUENCES_FILTER, KOTLIN_SEQUENCES_FIRST, KOTLIN_SEQUENCES_FIRST),
            CallChainConversion(KOTLIN_SEQUENCES_FILTER, KOTLIN_SEQUENCES_FIRST_OR_NULL, KOTLIN_SEQUENCES_FIRST_OR_NULL),
            CallChainConversion(KOTLIN_SEQUENCES_FILTER, KOTLIN_SEQUENCES_LAST, KOTLIN_SEQUENCES_LAST),
            CallChainConversion(KOTLIN_SEQUENCES_FILTER, KOTLIN_SEQUENCES_LAST_OR_NULL, KOTLIN_SEQUENCES_LAST_OR_NULL),
            CallChainConversion(KOTLIN_SEQUENCES_FILTER, KOTLIN_SEQUENCES_SINGLE, KOTLIN_SEQUENCES_SINGLE),
            CallChainConversion(KOTLIN_SEQUENCES_FILTER, KOTLIN_SEQUENCES_SINGLE_OR_NULL, KOTLIN_SEQUENCES_SINGLE_OR_NULL),
            CallChainConversion(KOTLIN_SEQUENCES_FILTER, KOTLIN_SEQUENCES_COUNT, KOTLIN_SEQUENCES_COUNT),
            CallChainConversion(KOTLIN_SEQUENCES_FILTER, KOTLIN_SEQUENCES_ANY, KOTLIN_SEQUENCES_ANY),
            CallChainConversion(KOTLIN_SEQUENCES_FILTER, KOTLIN_SEQUENCES_NONE, KOTLIN_SEQUENCES_NONE),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED, KOTLIN_SEQUENCES_FIRST_OR_NULL, KOTLIN_SEQUENCES_MIN),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED, KOTLIN_SEQUENCES_LAST_OR_NULL, KOTLIN_SEQUENCES_MAX),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED_DESCENDING, KOTLIN_SEQUENCES_FIRST_OR_NULL, KOTLIN_SEQUENCES_MAX),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED_DESCENDING, KOTLIN_SEQUENCES_LAST_OR_NULL, KOTLIN_SEQUENCES_MIN),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED_BY, KOTLIN_SEQUENCES_FIRST_OR_NULL, KOTLIN_SEQUENCES_MIN_BY),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED_BY, KOTLIN_SEQUENCES_LAST_OR_NULL, KOTLIN_SEQUENCES_MAX_BY),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED_BY_DESCENDING, KOTLIN_SEQUENCES_FIRST_OR_NULL, KOTLIN_SEQUENCES_MAX_BY),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED_BY_DESCENDING, KOTLIN_SEQUENCES_LAST_OR_NULL, KOTLIN_SEQUENCES_MIN_BY),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED, KOTLIN_SEQUENCES_FIRST, KOTLIN_SEQUENCES_MIN, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED, KOTLIN_SEQUENCES_LAST, KOTLIN_SEQUENCES_MAX, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED_DESCENDING, KOTLIN_SEQUENCES_FIRST, KOTLIN_SEQUENCES_MAX, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED_DESCENDING, KOTLIN_SEQUENCES_LAST, KOTLIN_SEQUENCES_MIN, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED_BY, KOTLIN_SEQUENCES_FIRST, KOTLIN_SEQUENCES_MIN_BY, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED_BY, KOTLIN_SEQUENCES_LAST, KOTLIN_SEQUENCES_MAX_BY, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED_BY_DESCENDING, KOTLIN_SEQUENCES_FIRST, KOTLIN_SEQUENCES_MAX_BY, addNotNullAssertion = true),
            CallChainConversion(KOTLIN_SEQUENCES_SORTED_BY_DESCENDING, KOTLIN_SEQUENCES_LAST, KOTLIN_SEQUENCES_MIN_BY, addNotNullAssertion = true),

            CallChainConversion(KOTLIN_SEQUENCES_MAP, KOTLIN_SEQUENCES_JOIN_TO, KOTLIN_SEQUENCES_JOIN_TO, enableSuspendFunctionCall = false),
            CallChainConversion(KOTLIN_SEQUENCES_MAP, KOTLIN_SEQUENCES_JOIN_TO_STRING, KOTLIN_SEQUENCES_JOIN_TO_STRING, enableSuspendFunctionCall = false),
            CallChainConversion(KOTLIN_SEQUENCES_MAP, KOTLIN_SEQUENCES_FILTER_NOT_NULL, KOTLIN_SEQUENCES_MAP_NOT_NULL),
            CallChainConversion(KOTLIN_SEQUENCES_MAP, KOTLIN_COLLECTIONS_TO_MAP, KOTLIN_SEQUENCES_ASSOCIATE),
            CallChainConversion(KOTLIN_SEQUENCES_MAP, KOTLIN_COLLECTIONS_TO_MAP, KOTLIN_SEQUENCES_ASSOCIATE_TO),
            CallChainConversion(
                KOTLIN_SEQUENCES_MAP, KOTLIN_SEQUENCES_SUM, KOTLIN_SEQUENCES_SUM_OF, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_SEQUENCES_MAP, KOTLIN_SEQUENCES_MAX, KOTLIN_SEQUENCES_MAX_OF,
                removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_SEQUENCES_MAP, KOTLIN_SEQUENCES_MAX, KOTLIN_SEQUENCES_MAX_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_4,
            ),
            CallChainConversion(
                KOTLIN_SEQUENCES_MAP, KOTLIN_SEQUENCES_MAX_OR_NULL, KOTLIN_SEQUENCES_MAX_OF,
                removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_SEQUENCES_MAP, KOTLIN_SEQUENCES_MAX_OR_NULL, KOTLIN_SEQUENCES_MAX_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_4,
            ),
            CallChainConversion(
                KOTLIN_SEQUENCES_MAP, KOTLIN_SEQUENCES_MIN, KOTLIN_SEQUENCES_MIN_OF,
                removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_SEQUENCES_MAP, KOTLIN_SEQUENCES_MIN, KOTLIN_SEQUENCES_MIN_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_4,
            ),
            CallChainConversion(
                KOTLIN_SEQUENCES_MAP, KOTLIN_SEQUENCES_MIN_OR_NULL, KOTLIN_SEQUENCES_MIN_OF,
                removeNotNullAssertion = true, replaceableApiVersion = ApiVersion.KOTLIN_1_4
            ),
            CallChainConversion(
                KOTLIN_SEQUENCES_MAP, KOTLIN_SEQUENCES_MIN_OR_NULL, KOTLIN_SEQUENCES_MIN_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_4,
            ),
            CallChainConversion(
                KOTLIN_SEQUENCES_MAP_NOT_NULL, KOTLIN_SEQUENCES_FIRST, KOTLIN_SEQUENCES_FIRST_NOT_NULL_OF,
                replaceableApiVersion = ApiVersion.KOTLIN_1_5
            ),
            CallChainConversion(
                KOTLIN_SEQUENCES_MAP_NOT_NULL, KOTLIN_SEQUENCES_FIRST_OR_NULL, KOTLIN_SEQUENCES_FIRST_NOT_NULL_OF_OR_NULL,
                replaceableApiVersion = ApiVersion.KOTLIN_1_5
            ),

            CallChainConversion(KOTLIN_SEQUENCES_MAP, KOTLIN_SEQUENCES_FLATTEN, KOTLIN_SEQUENCES_FLAT_MAP),
            CallChainConversion(KOTLIN_SEQUENCES_MAP_INDEXED, KOTLIN_SEQUENCES_FLATTEN, KOTLIN_SEQUENCES_FLAT_MAP_INDEXED),
        ).flatMap {
            it.withAdditionalMinMaxConversions()
        }
    }

    val collectionCreationConversions: List<CallChainConversion> by lazy {
        listOf(
            CallChainConversion(KOTLIN_COLLECTIONS_LIST_OF, KOTLIN_COLLECTIONS_FILTER_NOT_NULL, KOTLIN_COLLECTIONS_LIST_OF_NOT_NULL)
        )
    }

    val textOperationsConversions: List<CallChainConversion> by lazy {
        listOf(
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_FIRST, KOTLIN_TEXT_FIRST),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_FIRST_OR_NULL, KOTLIN_TEXT_FIRST_OR_NULL),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_LAST, KOTLIN_TEXT_LAST),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_LAST_OR_NULL, KOTLIN_TEXT_LAST_OR_NULL),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_SINGLE, KOTLIN_TEXT_SINGLE),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_SINGLE_OR_NULL, KOTLIN_TEXT_SINGLE_OR_NULL),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_IS_NOT_EMPTY, KOTLIN_TEXT_ANY),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_IS_EMPTY, KOTLIN_TEXT_NONE),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_COUNT, KOTLIN_TEXT_COUNT),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_ANY, KOTLIN_TEXT_ANY),
            CallChainConversion(KOTLIN_TEXT_FILTER, KOTLIN_TEXT_NONE, KOTLIN_TEXT_NONE),

            CallChainConversion(KOTLIN_TEXT_MAP, KOTLIN_COLLECTIONS_FLATTEN, KOTLIN_TEXT_FLAT_MAP),
            CallChainConversion(KOTLIN_TEXT_MAP_INDEXED, KOTLIN_COLLECTIONS_FLATTEN, KOTLIN_TEXT_FLAT_MAP_INDEXED),
        )
    }

    val conversionsList: List<CallChainConversion> by lazy {
        listOf(
            collectionsOperationsConversions,
            sequencesOperationsConversions,
            collectionCreationConversions,
            textOperationsConversions,
        ).flatten()
    }

    private fun CallChainConversion.withAdditionalMinMaxConversions(): List<CallChainConversion> {
        val original = this

        return when (original.replacementName) {
            MIN, MAX, MIN_BY, MAX_BY -> {
                val additionalConversion = if ((original.replacementName == MIN || original.replacementName == MAX) && original.addNotNullAssertion) {
                    original.copy(
                        replacementFqName = original.replacementFqName.sibling(Name.identifier("${original.replacementName}Of")),
                        replaceableApiVersion = ApiVersion.KOTLIN_1_4,
                        addNotNullAssertion = false,
                        additionalArgument = "{ it }"
                    )
                } else {
                    original.copy(
                        replacementFqName = original.replacementFqName.sibling(Name.identifier("${original.replacementName}OrNull")),
                        replaceableApiVersion = ApiVersion.KOTLIN_1_4
                    )
                }
                listOf(additionalConversion, original)
            }

            else -> listOf(original)
        }
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

    context(_: KaSession)
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

@ApiStatus.Internal
fun FqName.sibling(name: Name): FqName = parent().child(name)