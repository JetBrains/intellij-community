// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics.v2.flow

/**
 * Validation and transformation step for raw FUS (Feature Usage Statistics) values.
 */
sealed interface KotlinBuildToolFusFlowValidationStep<T> {
    /**
     * Processes a list of raw FUS values and returns a list of validated values of type [T].
     *
     * @param values The list of raw FUS values to process
     * @return A list of validated values of type [T], or null if validation fails
     */
    fun process(values: List<RawFusValue>): List<T>?
}

/**
 * Validation and transformation step of FUS value processing for string based on a regular expression pattern.
 * Only values that match the provided regex pattern will be included in the result.
 *
 * @property regex The regular expression pattern used for validation
 */
open class KotlinBuildToolStringRegexFusFlowValidationStep(val regex: Regex) : KotlinBuildToolFusFlowValidationStep<String> {
    override fun process(values: List<RawFusValue>): List<String> =
        values.map { it.value }.filter { regex.matches(it) }
}

/**
 * Validation and transformation step of FUS value processing for Boolean values.
 * Only values that can be strictly parsed as boolean ("true" or "false") will be included in the result.
 * Other values will be filtered out.
 */
class KotlinBuildToolBooleanFlowValidationStep : KotlinBuildToolFusFlowValidationStep<Boolean> {
    override fun process(values: List<RawFusValue>): List<Boolean> =
        values.mapNotNull { it.value.toBooleanStrictOrNull() }
}

/**
 * Validation and transformation step of FUS value processing for Long values.
 * Only values that can be parsed as Long numbers will be included in the result.
 * Non-numeric values will be filtered out.
 */
class KotlinBuildToolLongFlowValidationStep : KotlinBuildToolFusFlowValidationStep<Long> {
    override fun process(values: List<RawFusValue>): List<Long> =
        values.mapNotNull { it.value.toLongOrNull() }
}

/**
 * Validation and transformation step of FUS value processing for string values based on a whitelist of allowed values.
 * Only values that are present in the provided list of allowed values will be included in the result.
 *
 * @property allowedValues The list of string values that are considered valid
 */
class KotlinBuildToolStringAllowedValuesFlowValidationStep(val allowedValues: List<String>) : KotlinBuildToolFusFlowValidationStep<String> {
    override fun process(values: List<RawFusValue>): List<String> =
        values.map{ it.value }.filter { it in allowedValues }
}

/**
 * A decorator validation step that applies an additional filter to the results of another validation step.
 * This class wraps another validation step and applies a custom filter function to its results.
 *
 * @param T The type of values being processed (must be non-nullable)
 * @property validationStep The underlying validation step to use for initial processing
 * @property filter The predicate function used to further filter the validated values
 */
class FilteredKotlinBuildToolFusFlowValidationStep<T: Any>(val validationStep: KotlinBuildToolFusFlowValidationStep<T>, val filter: (T) -> Boolean) :
    KotlinBuildToolFusFlowValidationStep<T> {
    override fun process(values: List<RawFusValue>): List<T>? {
        return validationStep.process(values)?.filter(filter)
    }
}

/**
 * Basic validation and transformation step of FUS value processing for string values.
 * All input values are included in the result as-is.
 */
object KotlinBuildToolStringFLowValidationStep: KotlinBuildToolFusFlowValidationStep<String> {
    override fun process(values: List<RawFusValue>): List<String> {
        return values.map { it.value }
    }
}