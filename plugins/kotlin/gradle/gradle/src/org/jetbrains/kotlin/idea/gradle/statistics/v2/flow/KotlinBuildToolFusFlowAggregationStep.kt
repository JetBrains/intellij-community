// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics.v2.flow

import kotlin.math.abs


/**
 * Aggregation step of FUS value processing.
 */
interface KotlinBuildToolFusFlowAggregationStep<T> {
    /**
     * Aggregate a list of values and returns a single value or null.
     */
    fun process(values: List<T>): T?
}

/**
 * Aggregation step of FUS value processing.
 * Overrides all previous values with the last one.
 *
 * @param T The type of values being processed
 */
class OverrideValueAggregationStep<T> : KotlinBuildToolFusFlowAggregationStep<T> {
    override fun process(values: List<T>): T? =
        values.reduceOrNull { first, second -> second }
}

/**
 * Aggregation step of FUS value processing.
 * Joins all strings in the list into a single string with semicolon as a separator.
 */
class ConcatenateValuesAggregationStep(val separator: String) : KotlinBuildToolFusFlowAggregationStep<String> {
    override fun process(values: List<String>): String? =
        values.reduceOrNull { first, second -> "$first$separator$second" }
}

/**
 * Aggregation step of FUS value processing.
 * Aggregate Boolean metrics returns logical OR operation or null.
 */
class BooleanFlowAggregationStep : KotlinBuildToolFusFlowAggregationStep<Boolean> {
    override fun process(values: List<Boolean>): Boolean? =
        values.reduceOrNull { first, second -> first || second }

}

/**
 * Aggregation step of FUS value processing.
 * Aggregate Long metrics, returns the sum of all values or null.
 */
class SumLongValueAggregationStep : KotlinBuildToolFusFlowAggregationStep<Long> {
    override fun process(values: List<Long>): Long? =
        values.reduceOrNull { first, second -> first + second }
}

/**
 * Aggregation step of FUS value processing.
 * Aggregate Long values, returns the arithmetic mean of all values.
 */
class AverageLongValueAggregationStep : KotlinBuildToolFusFlowAggregationStep<Long> {
    override fun process(values: List<Long>): Long? =
        if (values.isEmpty()) null else
            values.reduce { first, second -> first + second } / values.size
}

/**
 * Aggregation step of FUS value processing.
 * Applies an anonymization function to the result of another aggregation step.
 */
class AnonymizeValueAggregationStep<T>(val aggregationStep: KotlinBuildToolFusFlowAggregationStep<T>, val anonymize: (T) -> T) :
    KotlinBuildToolFusFlowAggregationStep<T> {
    override fun process(values: List<T>): T? = aggregationStep.process(values)?.also { anonymize.invoke(it) }
}

/**
 * An anonymization function for Long that rounds the value to a "randomized" precision.
 */
fun Long.random10(): Long {
    if (abs(this) < 10) return this
    val sign = if (this < 0)
        -1
    else
        1
    val absT = this * sign
    var div: Long = 1
    while (div * 10 < absT) {
        div *= 10
    }
    return sign * if (absT / div < 2)
        absT - absT % (div / 10)
    else
        absT - absT % div
}