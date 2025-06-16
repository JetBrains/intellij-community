// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics.v2.flow

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.StringEventField
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.gradle.statistics.GradleStatisticsEventGroups

class FusFlowSendingStep(
    val eventName: GradleStatisticsEventGroups,
    metricNames: List<KotlinBuildToolFusMetricName>,
    val addIDEPluginVersion: Boolean = false
) {
    companion object {
        private val buildIdField = StringEventField.ValidatedByRegexp("buildId", "^[a-zA-Z0-9_-]*$")
    }

    private val metrics = metricNames.map { it.metric }

    fun getEventFields(): List<EventField<*>>{
        val eventFields = ArrayList<EventField<*>>()
        eventFields.add(buildIdField)
        if (addIDEPluginVersion) {
            eventFields.add(EventFields.PluginInfo)
        }
        eventFields.addAll(metrics.map { it.eventField })
        return eventFields
    }

    fun getEventPairs(buildId: String, aggregatedMetrics: Set<AggregatedFusMetric<*>>): Array<EventPair<*>> {
        val eventField = aggregatedMetrics.filter { metrics.contains(it.metric) }.map {
            it.toEventPair()
        }

        val buildIdEventPair = buildIdField.with(buildId)
        return if (addIDEPluginVersion) {
            arrayOf(buildIdEventPair, EventFields.PluginInfo.with(KotlinIdePlugin.getPluginInfo()), *eventField.toTypedArray())
        } else {
            arrayOf(buildIdEventPair, *eventField.toTypedArray())
        }
    }
}