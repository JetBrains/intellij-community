// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.RegistryManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface PiSessionUpdateEventsContributor {
  fun createUpdateEvents(watchedProjectPathsBySessionDir: StateFlow<Map<Path, Set<String>>>): Flow<AgentSessionSourceUpdateEvent>
}

internal fun createPiSessionUpdateEvents(
  watchedProjectPathsBySessionDir: StateFlow<Map<Path, Set<String>>>,
  fileWatchFallbackEnabledProvider: () -> Boolean,
  sessionUpdateEventsContributorProvider: () -> List<PiSessionUpdateEventsContributor>,
): Flow<AgentSessionSourceUpdateEvent> {
  if (!fileWatchFallbackEnabledProvider()) return emptyFlow()
  val contributorFlows = sessionUpdateEventsContributorProvider().map { contributor ->
    contributor.createUpdateEvents(watchedProjectPathsBySessionDir)
  }
  return when (contributorFlows.size) {
    0 -> emptyFlow()
    1 -> contributorFlows.single()
    else -> merge(*contributorFlows.toTypedArray())
  }
}

internal fun isPiFileWatchFallbackEnabled(): Boolean {
  return RegistryManager.getInstance().`is`(PI_FILE_WATCH_FALLBACK_REGISTRY_KEY)
}

internal fun piSessionUpdateEventsContributors(): List<PiSessionUpdateEventsContributor> {
  return PI_SESSION_UPDATE_EVENTS_CONTRIBUTOR_EP.extensionList
}

internal const val PI_FILE_WATCH_FALLBACK_REGISTRY_KEY: String = "agent.workbench.pi.file.watch.fallback"

private val PI_SESSION_UPDATE_EVENTS_CONTRIBUTOR_EP: ExtensionPointName<PiSessionUpdateEventsContributor> =
  ExtensionPointName("com.intellij.agent.workbench.pi.sessionUpdateEventsContributor")
