// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.core.script.v1.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider

internal class ScriptingSettingsFUSCollector: ProjectUsagesCollector() {
    override fun getGroup() = GROUP

    override fun getMetrics(project: Project): Set<MetricEvent> {
        if (KotlinPlatformUtils.isAndroidStudio) {
            return emptySet()
        }

        val metrics = mutableSetOf<MetricEvent>()
        val pluginInfo = getPluginInfoById(KotlinIdePlugin.id)

        // filling up scriptingAutoReloadEnabled Event
        project.service<ScriptDefinitionProvider>().currentDefinitions.forEach { definition ->
            if (definition.canAutoReloadScriptConfigurationsBeSwitchedOff) {
                val scriptingAutoReloadEnabled = KotlinScriptingSettings.getInstance(project).autoReloadConfigurations(definition)
                metrics.add(scriptingAREvent.metric(definition.name, scriptingAutoReloadEnabled, pluginInfo))
            }
        }

        return metrics
    }

    private val GROUP = EventLogGroup("kotlin.ide.script.settings", 1)

    // scriptingAutoReloadEnabled Event
    private val scriptingAREnabledField = EventFields.Boolean("enabled")
    private val scriptingDefNameField = EventFields.String(
        "definition_name", listOf(
            "KotlinInitScript",
            "KotlinSettingsScript",
            "KotlinBuildScript",
            "Script_definition_for_extension_scripts_and_IDE_console",
            "MainKtsScript",
            "Kotlin_Script",
            "Space_Automation"
        )
    )
    private val scriptingPluginInfoField = EventFields.PluginInfo

    private val scriptingAREvent = GROUP.registerEvent(
        "scriptingAutoReloadEnabled",
        scriptingDefNameField,
        scriptingAREnabledField,
        scriptingPluginInfoField
    )
}
