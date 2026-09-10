// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

internal object KotlinScriptingLifecycleCollector : CounterUsagesCollector() {
    private val GROUP = EventLogGroup("kotlin.ide.scripting.lifecycle", 1)

    private val configurationReloadedEvent = GROUP.registerEvent(
        "configuration.reloaded",
        EventFields.String("provider_id", REPORTED_PROVIDER_IDS),
        EventFields.Boolean("success"),
    )

    override fun getGroup(): EventLogGroup = GROUP

    fun logConfigurationReloaded(project: Project, definition: ScriptDefinition, success: Boolean) {
        configurationReloadedEvent.log(project, definition.reportedProviderId(), success)
    }
}
