// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialPromptDeliveryChannel
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialPromptDeliveryStatus
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialPromptRecord
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentTerminalPromptDispatch

internal data class AgentChatTabIdentity(
  @JvmField val projectHash: String,
  @JvmField val projectPath: String,
  @JvmField val threadIdentity: String,
  @JvmField val subAgentId: String?,
)

internal data class AgentChatTabRuntime(
  @JvmField val threadId: String,
  @JvmField val threadTitle: String,
  @JvmField val threadActivity: AgentThreadActivity,
  @JvmField val pendingCreatedAtMs: Long? = null,
  @JvmField val pendingFirstInputAtMs: Long? = null,
  @JvmField val pendingLaunchMode: String? = null,
  @JvmField val launchMode: String? = null,
  @JvmField val generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
  @JvmField val newThreadRebindRequestedAtMs: Long? = null,
  @JvmField val initialPromptRecord: AgentInitialPromptRecord? = null,
  @JvmField val terminalPromptDispatch: AgentTerminalPromptDispatch? = null,
) {
  val initialMessageDispatchSteps: List<AgentInitialMessageDispatchStep>
    get() = terminalPromptDispatch?.steps.orEmpty()

  val initialMessageDispatchStepIndex: Int
    get() = terminalPromptDispatch?.stepIndex ?: 0

  @Suppress("unused")
  val initialComposedMessage: String?
    get() = initialPromptRecord?.message

  val initialMessageToken: String?
    get() = initialPromptRecord?.token

  val initialMessageSent: Boolean
    get() = initialPromptRecord?.deliveryStatus == AgentInitialPromptDeliveryStatus.DELIVERED
}

internal data class AgentChatTabSnapshot(
  val tabKey: AgentChatTabKey,
  @JvmField val identity: AgentChatTabIdentity,
  @JvmField val runtime: AgentChatTabRuntime,
) {
  companion object {
    fun create(
      projectHash: String,
      projectPath: String,
      threadIdentity: String,
      threadId: String,
      threadTitle: String,
      subAgentId: String?,
      threadActivity: AgentThreadActivity = AgentThreadActivity.READY,
      pendingCreatedAtMs: Long? = null,
      pendingFirstInputAtMs: Long? = null,
      pendingLaunchMode: String? = null,
      launchMode: String? = null,
      generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
      newThreadRebindRequestedAtMs: Long? = null,
      initialMessageToken: String? = null,
      initialMessageSent: Boolean = false,
      initialMessageTimeoutPolicy: AgentInitialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK,
      initialMessageDispatchSteps: List<AgentInitialMessageDispatchStep> = emptyList(),
      initialMessageDispatchStepIndex: Int = 0,
      initialComposedMessage: String? = null,
      initialPromptRecord: AgentInitialPromptRecord? = null,
      terminalPromptDispatch: AgentTerminalPromptDispatch? = null,
    ): AgentChatTabSnapshot {
      val normalizedDispatchSteps = normalizeInitialMessageDispatchSteps(
        initialMessageDispatchSteps = initialMessageDispatchSteps,
        initialComposedMessage = initialComposedMessage,
        initialMessageTimeoutPolicy = initialMessageTimeoutPolicy,
      )
      val resolvedPromptRecord = initialPromptRecord
                                 ?: buildLegacyInitialPromptRecord(
                                   initialComposedMessage = initialComposedMessage,
                                   dispatchSteps = normalizedDispatchSteps,
                                   token = initialMessageToken,
                                   sent = initialMessageSent,
                                 )
      val resolvedTerminalDispatch = terminalPromptDispatch?.normalized()
                                     ?: AgentTerminalPromptDispatch(
                                       steps = normalizedDispatchSteps,
                                       stepIndex = initialMessageDispatchStepIndex,
                                     ).normalized()?.takeUnless { initialMessageSent }
      val identity = AgentChatTabIdentity(
        projectHash = projectHash,
        projectPath = projectPath,
        threadIdentity = threadIdentity,
        subAgentId = subAgentId,
      )
      return AgentChatTabSnapshot(
        tabKey = AgentChatTabKey.fromIdentity(identity),
        identity = identity,
        runtime = AgentChatTabRuntime(
          threadId = threadId,
          threadTitle = threadTitle,
          threadActivity = threadActivity,
          pendingCreatedAtMs = pendingCreatedAtMs,
          pendingFirstInputAtMs = pendingFirstInputAtMs,
          pendingLaunchMode = pendingLaunchMode,
          launchMode = normalizeAgentChatLaunchMode(launchMode),
          generationSettings = generationSettings,
          newThreadRebindRequestedAtMs = newThreadRebindRequestedAtMs,
          initialPromptRecord = resolvedPromptRecord,
          terminalPromptDispatch = resolvedTerminalDispatch,
        ),
      )
    }
  }
}

private fun normalizeInitialMessageDispatchSteps(
  initialMessageDispatchSteps: List<AgentInitialMessageDispatchStep>,
  initialComposedMessage: String?,
  initialMessageTimeoutPolicy: AgentInitialMessageTimeoutPolicy,
): List<AgentInitialMessageDispatchStep> {
  if (initialMessageDispatchSteps.isNotEmpty()) {
    return initialMessageDispatchSteps.filter(AgentInitialMessageDispatchStep::isDispatchable)
  }
  val normalizedMessage = initialComposedMessage?.trim().orEmpty()
  if (normalizedMessage.isEmpty()) {
    return emptyList()
  }
  return listOf(
    AgentInitialMessageDispatchStep(
      text = normalizedMessage,
      timeoutPolicy = initialMessageTimeoutPolicy,
    )
  )
}

private fun buildLegacyInitialPromptRecord(
  initialComposedMessage: String?,
  dispatchSteps: List<AgentInitialMessageDispatchStep>,
  token: String?,
  sent: Boolean,
): AgentInitialPromptRecord? {
  val message = initialComposedMessage?.trim()?.takeIf { it.isNotEmpty() }
                ?: dispatchSteps.lastOrNull { step -> step.action == AgentInitialMessageDispatchAction.SEND_TEXT && step.text.isNotBlank() }?.text
                ?: return null
  return AgentInitialPromptRecord(
    message = message,
    token = token,
    deliveryStatus = if (sent) {
      AgentInitialPromptDeliveryStatus.DELIVERED
    }
    else {
      AgentInitialPromptDeliveryStatus.PENDING
    },
    deliveryChannel = AgentInitialPromptDeliveryChannel.TERMINAL,
  )
}

internal sealed interface AgentChatTabResolution {
  val tabKey: AgentChatTabKey

  data class Resolved(
    @JvmField val snapshot: AgentChatTabSnapshot,
  ) : AgentChatTabResolution {
    override val tabKey: AgentChatTabKey = snapshot.tabKey
  }

  data class Unresolved(
    override val tabKey: AgentChatTabKey,
  ) : AgentChatTabResolution
}
