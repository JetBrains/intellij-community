// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

internal class K2MetricsFUSCollector : ApplicationUsagesCollector() {

    private val eventLogGroup = EventLogGroup("kotlin.k.two.metrics", 2)
    private val isK2EnabledField = EventFields.Boolean("is_k2_enabled")
    private val isK2EnabledEvent = eventLogGroup.registerEvent("enabled", isK2EnabledField)

    override fun getGroup(): EventLogGroup = eventLogGroup

    override fun getMetrics(): Set<MetricEvent> {
        return setOf(isK2EnabledEvent.metric(KotlinPluginModeProvider.isK2Mode()))
    }
}