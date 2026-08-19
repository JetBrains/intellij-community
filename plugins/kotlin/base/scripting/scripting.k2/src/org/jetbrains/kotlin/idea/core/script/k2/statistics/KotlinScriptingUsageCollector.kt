// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionProviderImpl
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.core.script.v1.settings.KotlinScriptingSettings
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

private val GROUP = EventLogGroup("kotlin.ide.scripting", 1)

private val definitionsCountEvent = GROUP.registerEvent(
    "definitions.count",
    EventFields.String("provider_id", REPORTED_PROVIDER_IDS),
    EventFields.Int("count"),
    EventFields.Int("disabled_count"),
)

private val externalProvidersEvent = GROUP.registerEvent(
    "external.providers.count",
    EventFields.Int("count"),
)

private val scriptsCountEvent = GROUP.registerEvent(
    "scripts.count",
    EventFields.String("provider_id", REPORTED_PROVIDER_IDS),
    EventFields.RoundedInt("count"),
)

internal class KotlinScriptingUsageCollector : ProjectUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    override suspend fun collect(project: Project): Set<MetricEvent> {
        if (KotlinPlatformUtils.isAndroidStudio) return emptySet()

        val settings = KotlinScriptingSettings.getInstance(project)
        val definitionProvider = ScriptDefinitionProviderImpl.getInstance(project)
        val providedDefinitions = definitionProvider.cachedProvidedDefinitions
        val enabledDefinitions = definitionProvider.currentDefinitions.toList()

        return buildSet {
            val definitionsPerProvider = providedDefinitions.groupBy { it.reportedProviderId() }
            val reportedIds = BUNDLED_PROVIDER_IDS + definitionsPerProvider.keys.filter { it !in BUNDLED_PROVIDER_IDS }
            reportedIds.forEach { providerId ->
                val definitions = definitionsPerProvider[providerId].orEmpty()
                add(
                    definitionsCountEvent.metric(
                        providerId,
                        definitions.size,
                        definitions.count { !settings.isScriptDefinitionEnabled(it) },
                    )
                )
            }

            val externalProvidersCount = ScriptDefinitionsProvider.EP_NAME.getExtensions(project)
                .count { getPluginInfo(it.javaClass).id != KotlinIdePlugin.id.idString }

            if (externalProvidersCount > 0) {
                add(externalProvidersEvent.metric(externalProvidersCount))
            }

            project.workspaceModel.currentSnapshot.entities(KotlinScriptEntity::class.java)
                .mapNotNull { it.virtualFileUrl.virtualFile }
                .mapNotNull { file -> enabledDefinitions.firstOrNull { it.isScript(VirtualFileScriptSource(file)) } }
                .groupingBy { it.reportedProviderId() }
                .eachCount()
                .forEach { (providerId, scripts) -> add(scriptsCountEvent.metric(providerId, scripts)) }
        }
    }
}
