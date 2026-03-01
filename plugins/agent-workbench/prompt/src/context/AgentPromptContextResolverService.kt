// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextContributorPhase
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextContributors
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextMetadataKeys
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger

private class AgentPromptContextResolverServiceLog

private val LOG = logger<AgentPromptContextResolverServiceLog>()

@Service(Service.Level.PROJECT)
internal class AgentPromptContextResolverService(
  private val contributorsProvider: () -> List<AgentPromptContextContributorBridge> = AgentPromptContextContributors::allBridges,
) {
  fun collectDefaultContext(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
    val invocationItems = collectByPhase(
      phase = AgentPromptContextContributorPhase.INVOCATION,
      invocationData = invocationData,
    )
    if (invocationItems.isNotEmpty()) {
      return invocationItems
    }

    return collectByPhase(
      phase = AgentPromptContextContributorPhase.FALLBACK,
      invocationData = invocationData,
    )
  }

  private fun collectByPhase(
    phase: AgentPromptContextContributorPhase,
    invocationData: AgentPromptInvocationData,
  ): List<AgentPromptContextItem> {
    val contributors = contributorsProvider().filter { contributor -> contributor.phase == phase }
    contributors.forEach { contributor ->
      val items = try {
        contributor.collect(invocationData)
      }
      catch (t: Throwable) {
        LOG.warn("Prompt context contributor failed: ${contributor::class.java.name}", t)
        emptyList()
      }
      if (items.isNotEmpty()) {
        return appendPhaseMetadata(items, phase)
      }
    }
    return emptyList()
  }

  private fun appendPhaseMetadata(
    items: List<AgentPromptContextItem>,
    phase: AgentPromptContextContributorPhase,
  ): List<AgentPromptContextItem> {
    val phaseValue = when (phase) {
      AgentPromptContextContributorPhase.INVOCATION -> "invocation"
      AgentPromptContextContributorPhase.FALLBACK -> "fallback"
    }
    return items.map { item ->
      if (item.metadata[AgentPromptContextMetadataKeys.PHASE] == phaseValue) {
        item
      }
      else {
        val metadata = LinkedHashMap(item.metadata)
        metadata.putIfAbsent(AgentPromptContextMetadataKeys.PHASE, phaseValue)
        item.copy(metadata = metadata)
      }
    }
  }
}
