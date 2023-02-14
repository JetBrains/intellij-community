/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.gradle.statistics

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.text.trimMiddle
import org.jetbrains.kotlin.statistics.fileloggers.MetricsContainer
import org.jetbrains.kotlin.statistics.metrics.BooleanMetrics
import org.jetbrains.kotlin.statistics.metrics.NumericalMetrics
import org.jetbrains.kotlin.statistics.metrics.StringMetrics
import java.util.*
import kotlin.collections.HashMap

interface ICustomMetric

typealias NumericalMetricConsumer = (MetricsContainer /*current*/, MetricsContainer? /*previous*/) -> Long?
typealias BooleanMetricConsumer = (MetricsContainer /*current*/, MetricsContainer? /*previous*/) -> Boolean?
typealias StringMetricConsumer = (MetricsContainer /*current*/, MetricsContainer? /*previous*/) -> String?

enum class CustomNumericalMetrics(val eventGroup: GradleStatisticsEventGroups, val consumer: NumericalMetricConsumer) : ICustomMetric {
    TIME_BETWEEN_BUILDS(GradleStatisticsEventGroups.UseScenarios, { current, previous ->
        val finishTime = current.getMetric(NumericalMetrics.BUILD_FINISH_TIME)?.getValue()
        val prevFinishTime = previous?.getMetric(NumericalMetrics.BUILD_FINISH_TIME)?.getValue()
        if (finishTime != null && prevFinishTime != null) finishTime - prevFinishTime else null
    })
}

class KotlinGradleEvent(group: EventLogGroup, val eventName: GradleStatisticsEventGroups, vararg metrics: Any) {

    private val booleanEventFields = TreeMap<String, EventField<Boolean>>() // we prefer sorted collection for event creation
    private val booleanMetricConsumers = HashMap<String, BooleanMetricConsumer>()
    private val numericalEventFields = TreeMap<String, EventField<Long>>()
    private val numericalMetricConsumers = HashMap<String, NumericalMetricConsumer>()
    private val stringEventFields = TreeMap<String, EventField<String?>>()
    private val stringMetricConsumers = HashMap<String, StringMetricConsumer>()

    private fun allEventFieldsValues() = booleanEventFields.values.union(numericalEventFields.values).union(stringEventFields.values)

    val eventId: VarargEventId

    init {
        metrics.forEach {
            when (it) {
                is BooleanMetrics -> {
                    booleanEventFields[it.name] = EventFields.Boolean(it.name.lowercase())
                    booleanMetricConsumers[it.name] = { current, _ -> current.getMetric(it)?.getValue() }
                }
                is NumericalMetrics -> {
                    numericalEventFields[it.name] = EventFields.Long(it.name.lowercase())
                    numericalMetricConsumers[it.name] = { current, _ -> current.getMetric(it)?.getValue() }
                }
                is StringMetrics -> {
                    stringEventFields[it.name] = EventFields.StringValidatedByInlineRegexp(it.name.lowercase(), it.anonymization.validationRegexp())
                    stringMetricConsumers[it.name] = { current, _ ->
                        current.getMetric(it)?.getValue()?.anonymizeIdeString(it)
                    }
                }
                else -> throw IllegalArgumentException("$it is of unknown metric type.")
            }
        }
        CustomNumericalMetrics.values().forEach {
            if (it.eventGroup == eventName || eventName == GradleStatisticsEventGroups.All) {
                numericalEventFields[it.name] = EventFields.Long(it.name.lowercase())
                numericalMetricConsumers[it.name] = it.consumer
            }
        }
        eventId = if (addIDEPluginVersion(eventName))
            group.registerVarargEvent(eventName.name, EventFields.PluginInfo, *allEventFieldsValues().toTypedArray())
        else
            group.registerVarargEvent(eventName.name, *allEventFieldsValues().toTypedArray())
    }

    fun getEventPairs(currentMetrics: MetricsContainer, previousMetrics: MetricsContainer?): Array<EventPair<*>> {
        val booleanEventPairs = booleanEventFields.entries.mapNotNull {
            val value = booleanMetricConsumers[it.key]?.invoke(currentMetrics, previousMetrics)
            if (value != null)
                EventPair(it.value, value)
            else
                null
        }
        val numericalEventPairs = numericalEventFields.entries.mapNotNull {
            val value = numericalMetricConsumers[it.key]?.invoke(currentMetrics, previousMetrics)
            if (value != null)
                EventPair(it.value, value)
            else
                null
        }
        val stringEventPairs = stringEventFields.entries.mapNotNull {
            val value = stringMetricConsumers[it.key]?.invoke(currentMetrics, previousMetrics)
            if (value != null)
                EventPair(it.value, value)
            else
                null
        }
        return booleanEventPairs.union(numericalEventPairs).union(stringEventPairs).toTypedArray()
    }

    companion object {

        internal fun addIDEPluginVersion(event: GradleStatisticsEventGroups) = event == GradleStatisticsEventGroups.ComponentVersions
                || event == GradleStatisticsEventGroups.All


        private val IDE_STRING_ANONYMIZERS = lazy {
            mapOf(
                StringMetrics.PROJECT_PATH to { path: String ->
                    // This code duplicated logics of StatisticsUtil.getProjectId, which could not be directly reused:
                    // 1. the path of gradle project may not have corresponding project
                    // 2. the projectId should be stable and independent on IDE version
                    val presentableUrl = FileUtil.toSystemIndependentName(path)
                    val name =
                        PathUtilRt.getFileName(presentableUrl).lowercase(Locale.US).removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION)
                    val locationHash = Integer.toHexString((presentableUrl).hashCode())
                    val projectHash =
                        "${name.trimMiddle(name.length.coerceAtMost(254 - locationHash.length), useEllipsisSymbol = false)}.$locationHash"
                    @Suppress("DEPRECATION")
                    EventLogConfiguration.getInstance().anonymize(projectHash)
                })
        }

        private fun String.anonymizeIdeString(metric: StringMetrics) = if (metric.anonymization.anonymizeOnIdeSize())
            IDE_STRING_ANONYMIZERS.value[metric]?.invoke(this)
        else
            this

    }
}
