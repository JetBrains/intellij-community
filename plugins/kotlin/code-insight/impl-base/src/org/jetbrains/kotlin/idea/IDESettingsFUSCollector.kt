// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightSettings
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightWorkspaceSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin

class IDESettingsFUSCollector : ProjectUsagesCollector() {
    override fun getGroup() = GROUP

    override fun getMetrics(project: Project): Set<MetricEvent> {
        if (KotlinPlatformUtils.isAndroidStudio) {
            return emptySet()
        }

        val metrics = mutableSetOf<MetricEvent>()
        val pluginInfo = getPluginInfoById(KotlinIdePlugin.id)

        val settings: KotlinCodeInsightSettings = KotlinCodeInsightSettings.getInstance()
        val projectSettings: KotlinCodeInsightWorkspaceSettings = KotlinCodeInsightWorkspaceSettings.getInstance(project)

        // filling up addUnambiguousImportsOnTheFly and optimizeImportsOnTheFly Events
        metrics.add(unambiguousImportsEvent.metric(settings.addUnambiguousImportsOnTheFly, pluginInfo))
        metrics.add(optimizeImportsEvent.metric(projectSettings.optimizeImportsOnTheFly, pluginInfo))

        return metrics
    }

    private val GROUP = EventLogGroup("kotlin.ide.settings", 5)


    // addUnambiguousImportsOnTheFly Event
    private val unambiguousImportsEvent =
        GROUP.registerEvent("addUnambiguousImportsOnTheFly", EventFields.Boolean("enabled"), EventFields.PluginInfo)

    // optimizeImportsOnTheFly Event
    private val optimizeImportsEvent =
        GROUP.registerEvent("optimizeImportsOnTheFly", EventFields.Boolean("enabled"), EventFields.PluginInfo)
}