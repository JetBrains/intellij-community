// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin


class K2MetricsFUSCollector : ApplicationUsagesCollector() {

    override fun getGroup(): EventLogGroup {
        return Companion.group
    }
    override fun getMetrics(): Set<MetricEvent> {
        val metrics = HashSet<MetricEvent>()

        metrics.add(isK2EnabledEvent.metric(isK2Plugin()))

        return metrics
    }

    companion object {
        private val group = EventLogGroup("kotlin.k.two.metrics", 1)
        private val isK2EnabledField = EventFields.Boolean("isK2Enabled")
        private val isK2EnabledEvent = Companion.group.registerEvent("enabled", isK2EnabledField)
    }

}