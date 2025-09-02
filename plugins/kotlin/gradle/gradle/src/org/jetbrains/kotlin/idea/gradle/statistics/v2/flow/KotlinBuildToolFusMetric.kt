// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics.v2.flow

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.StringEventField
import kotlin.String

/**
 * Base class for all Kotlin build tool Feature Usage Statistics (FUS) metrics.
 * This class defines the common structure and behavior for different types of metrics.
 *
 * @param T The type of the metric value (must be non-nullable)
 * @property metricRawName The name of the metric used in FUS files
 * @property eventField The event field used for reporting the metric into FUS backend
 * @property validationStep The validation step used to validate and transform raw values
 * @property aggregationStep The aggregation step used to aggregate validated values into a single result
 */
sealed class KotlinBuildToolFusMetric<T : Any>(
    val metricRawName: String,
    val eventField: EventField<T>,
    val validationStep: KotlinBuildToolFusFlowValidationStep<T>,
    val aggregationStep: KotlinBuildToolFusFlowAggregationStep<T>
) {
    /**
     * Processes a list of raw FUS values through validation and aggregation steps.
     * 
     * @param values The list of raw FUS values to process
     * @return An aggregated metric with the processed value, or null if validation or aggregation fails
     */
    fun process(values: List<RawFusValue>): AggregatedFusMetric<T>? =
        validationStep.process(values)?.let { aggregationStep.process(it) }?.let { AggregatedFusMetric(this, it) }
}

/**
 * Represents an aggregated FUS metric with its value.
 * This class holds the result of processing raw values through validation and aggregation steps.
 *
 * @param T The type of the metric value (must be non-nullable)
 * @property metric The metric that produced this aggregated value
 * @property value The aggregated value of the metric
 */
class AggregatedFusMetric<T : Any>(val metric: KotlinBuildToolFusMetric<T>, val value: T) {
    /**
     * Converts this aggregated metric to an event pair for reporting.
     *
     * @return An event pair containing the metric's event field and value
     */
    fun toEventPair(): EventPair<T> = EventPair(metric.eventField, value)
}

//Boolean metrics
/**
 * Base class for Boolean FUS metrics.
 * 
 * @param metric The name of the metric
 * @param aggregationStep The aggregation step to use for this metric
 */
internal open class BooleanFusMetric(metric: String, aggregationStep: KotlinBuildToolFusFlowAggregationStep<Boolean>) :
    KotlinBuildToolFusMetric<Boolean>(
        metric,
        eventField = EventFields.Boolean(metric.lowercase()),
        validationStep = KotlinBuildToolBooleanFlowValidationStep(),
        aggregationStep = aggregationStep
    )

/**
 * Boolean FUS metric that uses logical OR aggregation.
 * The final value will be true if any of the raw values is true.
 * 
 * @param metric The name of the metric
 */
internal class KotlinBuildToolBooleanFusMetric(metric: String) : BooleanFusMetric(metric, BooleanFlowAggregationStep())

/**
 * Boolean FUS metric that uses override aggregation.
 * The final value will be the last valid value in the list.
 * 
 * @param metric The name of the metric
 */
internal class KotlinBuildToolBooleanOverrideFusMetric(metric: String) : BooleanFusMetric(metric, OverrideValueAggregationStep())

//Long metrics
/**
 * Base class for Long FUS metrics.
 * 
 * @param metric The name of the metric
 * @param aggregationStep The aggregation step to use for this metric
 */
internal open class LongFusMetric(metric: String, aggregationStep: KotlinBuildToolFusFlowAggregationStep<Long>) :
    KotlinBuildToolFusMetric<Long>(
        metric,
        eventField = EventFields.Long(metric.lowercase()),
        validationStep = KotlinBuildToolLongFlowValidationStep(),
        aggregationStep = aggregationStep
    )

/**
 * Long FUS metric that uses override aggregation.
 * The final value will be the last valid value in the list.
 * 
 * @param metric The name of the metric
 */
internal class KotlinBuildToolLongOverrideFusMetric(metric: String) :
    LongFusMetric(metric, aggregationStep = OverrideValueAggregationStep())

/**
 * Long FUS metric that uses sum aggregation.
 * The final value will be the sum of all valid values.
 * 
 * @param metric The name of the metric
 */
internal class KotlinBuildToolLongSumFusMetric(metric: String) : LongFusMetric(metric, aggregationStep = SumLongValueAggregationStep())

/**
 * Long FUS metric that uses sum aggregation and then anonymizes the result.
 * The final value will be the sum of all valid values, anonymized using the random10 function.
 * 
 * @param metric The name of the metric
 */
internal class KotlinBuildToolLongSumAndRandomFusMetric(metric: String) :
    AnonymizedKotlinBuildToolFusMetric<Long>(KotlinBuildToolLongSumFusMetric(metric), Long::random10)

/**
 * Long FUS metric that uses average aggregation.
 * The final value will be the arithmetic mean of all valid values.
 * 
 * @param metric The name of the metric
 */
internal class KotlinBuildToolLongAverageFusMetric(metric: String) :
    LongFusMetric(metric, aggregationStep = AverageLongValueAggregationStep())

//String metrics
/**
 * Base class for String FUS metrics validated by a regular expression.
 * 
 * @param metric The name of the metric
 * @param regex The regular expression pattern used for validation
 * @param aggregationStep The aggregation step to use for this metric
 */
internal open class RegexStringFusMetric(metric: String, regex: String, aggregationStep: KotlinBuildToolFusFlowAggregationStep<String>) :
    KotlinBuildToolFusMetric<String>(
        metric,
        eventField = EventFields.StringValidatedByInlineRegexp(metric.lowercase(), regex) as EventField<String>,
        validationStep = KotlinBuildToolStringRegexFusFlowValidationStep(Regex(regex)),
        aggregationStep = aggregationStep
    )

/**
 * String FUS metric that concatenates all values from a list of allowed values.
 * The final value will be a semicolon-separated string of all valid values.
 * 
 * @param metric The name of the metric
 * @param allowedValues The list of allowed values for validation
 */
internal class ConcatenatedAllowedListValuesStringFusMetric(metric: String, allowedValues: List<String>) :
    KotlinBuildToolFusMetric<String>(
        metric,
        eventField = EventFields.StringValidatedByInlineRegexp(metric.lowercase(), allowedValues.joinToString(prefix = "((", postfix = ");?)+", separator = "|"),
        ) as EventField<String>,
        validationStep = KotlinBuildToolStringAllowedValuesFlowValidationStep(allowedValues),
        aggregationStep = ConcatenateValuesAggregationStep(";")
    )

/**
 * String FUS metric that uses override aggregation with regex validation.
 * The final value will be the last valid value in the list that matches the regex.
 * 
 * @param metric The name of the metric
 * @param regex The regular expression pattern used for validation
 */
internal class OverrideRegexStringFusMetric(metric: String, regex: String) :
    RegexStringFusMetric(metric, regex, OverrideValueAggregationStep())

/**
 * Special FUS metric for file and directory paths.
 * Uses EventFields.AnonymizedPath to provide anonymization for user paths.
 *
 * @param metric The name of the metric
 */
internal class PathFusMetric(metric: String) : KotlinBuildToolFusMetric<String>(
    metric,
    EventFields.AnonymizedPath as EventField<String>,
    validationStep = KotlinBuildToolStringFLowValidationStep,
    aggregationStep = OverrideValueAggregationStep()
)

/**
 * Special FUS metric for build IDs.
 * Validates that the build ID contains only alphanumeric characters, underscores, and hyphens.
 */
internal class BuildIdFusMetric : KotlinBuildToolFusMetric<String>(
    "BUILD_ID",
    eventField = StringEventField.ValidatedByInlineRegexp("buildId", "^[a-zA-Z0-9_-]*$") as EventField<String>,
    validationStep = KotlinBuildToolStringRegexFusFlowValidationStep(Regex("^[a-zA-Z0-9_-]*$")),
    OverrideValueAggregationStep()
)

/**
 * String FUS metric for version strings.
 * Validates version strings in the format: major.minor.patch[-suffix]
 * where suffix can be dev, snapshot, m, rc, beta.
 * 
 * @param metric The name of the metric
 */
internal class VersionStringFusMetric(metric: String) : RegexStringFusMetric(
    metric,
    "(\\d+).(\\d+).(\\d+)-?(dev|snapshot|m\\d?|rc\\d?|beta\\d?)?",
    OverrideValueAggregationStep()
)

/**
 * String FUS metric for version strings that ignores the default "0.0.0" version.
 * Validates version strings in the format: major.minor.patch[-suffix]
 * where suffix can be dev, snapshot, m, rc, beta.
 * 
 * @param metric The name of the metric
 */
internal class IgnoreDefaultVersionStringFusMetric(metric: String) : KotlinBuildToolFusMetric<String>(
    metric,
    eventField = EventFields.StringValidatedByInlineRegexp(
        metric.lowercase(),
        "(\\d+).(\\d+).(\\d+)-?(dev|snapshot|m\\d?|rc\\d?|beta\\d?)?"
    ) as EventField<String>,
    validationStep = FilteredKotlinBuildToolFusFlowValidationStep(
        KotlinBuildToolStringRegexFusFlowValidationStep(Regex("(\\d+).(\\d+).(\\d+)-?(dev|snapshot|m\\d?|rc\\d?|beta\\d?)?"))
    ) {
      it != "0.0.0"
    },
    aggregationStep = OverrideValueAggregationStep()
)

/**
 * A decorator for FUS metrics that applies anonymization to the aggregated value.
 * This class wraps another metric and applies an anonymization function to its result.
 *
 * @param T The type of the metric value (must be non-nullable)
 * @property unanonymizedKotlinBuildToolFusMetrics The underlying metric to anonymize
 * @property anonymization The function used to anonymize the aggregated value
 */
open class AnonymizedKotlinBuildToolFusMetric<T : Any>(
    unanonymizedKotlinBuildToolFusMetrics: KotlinBuildToolFusMetric<T>,
    anonymization: (T) -> T
) : KotlinBuildToolFusMetric<T>(
    unanonymizedKotlinBuildToolFusMetrics.metricRawName,
    unanonymizedKotlinBuildToolFusMetrics.eventField,
    unanonymizedKotlinBuildToolFusMetrics.validationStep,
    AnonymizeValueAggregationStep(unanonymizedKotlinBuildToolFusMetrics.aggregationStep, anonymization)
)
