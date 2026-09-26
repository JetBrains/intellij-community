// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

internal object KotlinScriptingSettingsCollector : CounterUsagesCollector() {
    private val GROUP = EventLogGroup("kotlin.ide.scripting.settings", 1)

    private val definitionsReloadedEvent = GROUP.registerEvent("definitions.reloaded")

    private val definitionsReorderedEvent = GROUP.registerEvent("definitions.reordered")

    private val manualDefinitionsAddedEvent = GROUP.registerEvent(
        "manual.definitions.added",
        EventFields.Int("definition_classes"),
        EventFields.Int("classpath_entries"),
    )

    private val configurationReloadedEvent = GROUP.registerEvent(
        "configuration.reloaded",
        EventFields.String("provider_id", REPORTED_PROVIDER_IDS),
        EventFields.Boolean("success"),
    )

    override fun getGroup(): EventLogGroup = GROUP

    fun logDefinitionsReloaded(project: Project) {
        definitionsReloadedEvent.log(project)
    }

    fun logDefinitionsReordered(project: Project) {
        definitionsReorderedEvent.log(project)
    }

    fun logManualDefinitionsAdded(project: Project, definitionClasses: Int, classpathEntries: Int) {
        manualDefinitionsAddedEvent.log(project, definitionClasses, classpathEntries)
    }

    fun logConfigurationReloaded(project: Project, definition: ScriptDefinition, success: Boolean) {
        configurationReloadedEvent.log(project, definition.reportedProviderId(), success)
    }
}
