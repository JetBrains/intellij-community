// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.builtins.StandardNames.BUILT_INS_PACKAGE_FQ_NAME
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.name.StandardClassIds.BASE_COLLECTIONS_PACKAGE
import org.jetbrains.kotlin.name.StandardClassIds.BASE_ENUMS_PACKAGE
import org.jetbrains.kotlin.name.StandardClassIds.BASE_SEQUENCES_PACKAGE

@ApiStatus.Internal
object StandardKotlinNames {
    val KOTLIN_IO_PACKAGE: FqName = BUILT_INS_PACKAGE_FQ_NAME + "io"
    val KOTLIN_TIME_PACKAGE: FqName = BUILT_INS_PACKAGE_FQ_NAME + "time"

    object Boolean {
        @JvmField val not: FqName = (BUILT_INS_PACKAGE_FQ_NAME + "Boolean") + "not"
    }
    object Collections {
        @JvmField val asSequence: FqName = BASE_COLLECTIONS_PACKAGE + "asSequence"
        @JvmField val filter: FqName = BASE_COLLECTIONS_PACKAGE + "filter"
        @JvmField val filterIsInstance: FqName = BASE_COLLECTIONS_PACKAGE + "filterIsInstance"
        @JvmField val filterNotNull: FqName = BASE_COLLECTIONS_PACKAGE + "filterNotNull"
        @JvmField val flatMap: FqName = BASE_COLLECTIONS_PACKAGE + "flatMap"
        @JvmField val flatten: FqName = BASE_COLLECTIONS_PACKAGE + "flatten"
        @JvmField val map: FqName = BASE_COLLECTIONS_PACKAGE + "map"
        @JvmField val mapIndexed: FqName = BASE_COLLECTIONS_PACKAGE + "mapIndexed"
        @JvmField val emptyList: FqName = BASE_COLLECTIONS_PACKAGE + "emptyList"
        @JvmField val emptyMap: FqName = BASE_COLLECTIONS_PACKAGE + "emptyMap"
        @JvmField val emptySet: FqName = BASE_COLLECTIONS_PACKAGE + "emptySet"
        @JvmField val listOf: FqName = BASE_COLLECTIONS_PACKAGE + "listOf"
        @JvmField val mapOf: FqName = BASE_COLLECTIONS_PACKAGE + "mapOf"
        @JvmField val setOf: FqName = BASE_COLLECTIONS_PACKAGE + "setOf"

        @JvmField val transformations: List<FqName> =
            collectionTransformationFunctionNames.map { BASE_COLLECTIONS_PACKAGE + it }

        @JvmField val terminations: List<FqName> = collectionTerminationFunctionNames.map {
            val pkg = if (it in listOf("contains", "indexOf", "lastIndexOf")) {
                BASE_COLLECTIONS_PACKAGE + "List"
            } else {
                BASE_COLLECTIONS_PACKAGE
            }
            pkg + it
        }
    }

    object Enum {
        @JvmField val enumEntries: FqName = BASE_ENUMS_PACKAGE + "enumEntries"
        @JvmField val enumValues: FqName = BUILT_INS_PACKAGE_FQ_NAME + "enumValues"
        @JvmField val enumValueOf: FqName = BUILT_INS_PACKAGE_FQ_NAME + "enumValueOf"
        @JvmField val enumEntriesTopLevelFunction: CallableId = CallableId(BASE_ENUMS_PACKAGE, enumEntries.shortName())
    }

    @JvmField val exceptionClassId: ClassId = ClassId(BUILT_INS_PACKAGE_FQ_NAME, Name.identifier("Exception"))
    @JvmField val throwableClassId: ClassId = ClassId.topLevel( StandardNames.FqNames.throwable)

    object Jvm {
        @JvmField val JvmInline: FqName = JvmStandardClassIds.BASE_JVM_PACKAGE + "JvmInline"
    }

    object Sequences {
        @JvmField val asSequence: FqName = BASE_SEQUENCES_PACKAGE + "asSequence"

        @JvmField val Sequence: FqName = BASE_SEQUENCES_PACKAGE + "Sequence"

        @JvmField val sequence: FqName = BASE_SEQUENCES_PACKAGE + "sequence"
        @JvmField val yield: FqName = BASE_SEQUENCES_PACKAGE + "SequenceScope.yield"
        @JvmField val yieldAll: FqName = BASE_SEQUENCES_PACKAGE + "SequenceScope.yieldAll"

        @JvmField val terminations: List<FqName> =
            collectionTerminationFunctionNames.map { BASE_SEQUENCES_PACKAGE + it }

        @JvmField val transformations: List<FqName> =
            collectionTransformationFunctionNames.map { BASE_SEQUENCES_PACKAGE + it }
    }

    @JvmField val also: FqName = BUILT_INS_PACKAGE_FQ_NAME + "also"
    @JvmField val lazy: FqName = BUILT_INS_PACKAGE_FQ_NAME + "lazy"
    @JvmField val let: FqName = BUILT_INS_PACKAGE_FQ_NAME + "let"
    @JvmField val run: FqName = BUILT_INS_PACKAGE_FQ_NAME + "run"
    @JvmField val takeIf: FqName = BUILT_INS_PACKAGE_FQ_NAME + "takeIf"
    @JvmField val takeUnless: FqName = BUILT_INS_PACKAGE_FQ_NAME + "takeUnless"

    private val collectionTransformationFunctionNames = listOf(
        "chunked",
        "distinct",
        "distinctBy",
        "drop",
        "dropWhile",
        "filter",
        "filterIndexed",
        "filterIsInstance",
        "filterNot",
        "filterNotNull",
        "flatMap",
        "flatMapIndexed",
        "flatten",
        "map",
        "mapIndexed",
        "mapIndexedNotNull",
        "mapNotNull",
        "minus",
        "minusElement",
        "onEach",
        "onEachIndexed",
        "plus",
        "plusElement",
        "requireNoNulls",
        "runningFold",
        "runningFoldIndexed",
        "runningReduce",
        "runningReduceIndexed",
        "scan",
        "scanIndexed",
        "sorted",
        "sortedBy",
        "sortedByDescending",
        "sortedDescending",
        "sortedWith",
        "take",
        "takeWhile",
        "windowed",
        "withIndex",
        "zipWithNext"
    )

    private val collectionTerminationFunctionNames = listOf(
        "all",
        "any",
        "asIterable",
        "asSequence",
        "associate",
        "associateBy",
        "associateByTo",
        "associateTo",
        "average",
        "contains",
        "count",
        "elementAt",
        "elementAtOrElse",
        "elementAtOrNull",
        "filterIndexedTo",
        "filterIsInstanceTo",
        "filterNotNullTo",
        "filterNotTo",
        "filterTo",
        "find",
        "findLast",
        "first",
        "firstNotNullOf",
        "firstNotNullOfOrNull",
        "firstOrNull",
        "flatMapTo",
        "flatMapIndexedTo",
        "fold",
        "foldIndexed",
        "groupBy",
        "groupByTo",
        "groupingBy",
        "indexOf",
        "indexOfFirst",
        "indexOfLast",
        "joinTo",
        "joinToString",
        "last",
        "lastIndexOf",
        "lastOrNull",
        "mapIndexedNotNullTo",
        "mapIndexedTo",
        "mapNotNullTo",
        "mapTo",
        "maxOrNull",
        "maxByOrNull",
        "maxWithOrNull",
        "maxOf",
        "maxOfOrNull",
        "maxOfWith",
        "maxOfWithOrNull",
        "minOrNull",
        "minByOrNull",
        "minWithOrNull",
        "minOf",
        "minOfOrNull",
        "minOfWith",
        "minOfWithOrNull",
        "none",
        "partition",
        "reduce",
        "reduceIndexed",
        "reduceIndexedOrNull",
        "reduceOrNull",
        "single",
        "singleOrNull",
        "sum",
        "sumBy",
        "sumByDouble",
        "sumOf",
        "toCollection",
        "toHashSet",
        "toList",
        "toMutableList",
        "toMutableSet",
        "toSet",
        "toSortedSet",
        "unzip"
    )

}
