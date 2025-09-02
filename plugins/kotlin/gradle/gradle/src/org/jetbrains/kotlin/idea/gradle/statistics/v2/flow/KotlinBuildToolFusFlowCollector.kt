// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics.v2.flow

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object KotlinBuildToolFusFlowCollector : CounterUsagesCollector() {
    /**
     * The group version is used for the events validation on fus backend.
     *
     * IMPORTANT: The group version has to be increased with every change of the events.
     */
    private const val GROUP_VERSION: Int = 3

    /**
     * The FUS backend uses the event version for event validation.
     * As the current pipline allows adding new metrics without changing the Kotlin project,
     * therefore these metrics will not be present in events from the legacy pipeline
     */
    private val GROUP = EventLogGroup(
        "kotlin.gradle.performance_v2",
        GROUP_VERSION,
        recorder = "FUS",
        description = "Kotlin build performance statistics collected from Kotlin Gradle plugin and projects using the FUS Gradle plugin"
    )

    override fun getGroup(): EventLogGroup = GROUP
    private val eventIds: Map<FusFlowSendingStep, VarargEventId> = kotlinBuildToolsFusEvenList.associateWith {
        group.registerVarargEvent(it.eventName.name, *it.getEventFields().toTypedArray())
    }

    fun send(event: FusFlowSendingStep, buildId: String, aggregateMetrics: Set<AggregatedFusMetric<*>>) {
        eventIds[event]?.log(*event.getEventPairs(buildId, aggregateMetrics))
    }

}
