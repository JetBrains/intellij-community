// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.TestOnly

private class AgentChatVirtualFileLog

private val LOG = logger<AgentChatVirtualFileLog>()

internal class AgentChatVirtualFile internal constructor(
  private val fileSystem: AgentChatVirtualFileSystem,
  resolution: AgentChatTabResolution,
) : LightVirtualFile(resolveFileName(resolution.tabKey.value)) {
  private val key: AgentChatTabKey = resolution.tabKey

  val tabKey: String
    get() = key.value

  var projectHash: String = ""
    private set

  var projectPath: String = ""
    private set

  var threadIdentity: String = ""
    private set

  var provider: AgentSessionProvider? = null
    private set

  var sessionId: String = ""
    private set

  var isPendingThread: Boolean = false
    private set

  var subAgentId: String? = null
    private set

  var shellCommand: List<String> = emptyList()
    private set

  var shellEnvVariables: Map<String, String> = emptyMap()
    private set

  @Volatile
  private var startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec? = null

  var threadId: String = ""
    private set

  @NlsSafe
  var threadTitle: String = resolveThreadTitle("")
    private set

  var threadActivity: AgentThreadActivity = AgentThreadActivity.READY
    private set

  var pendingCreatedAtMs: Long? = null
    private set

  var pendingFirstInputAtMs: Long? = null
    private set

  var pendingLaunchMode: String? = null
    private set

  var newThreadRebindRequestedAtMs: Long? = null
    private set

  var initialMessageDispatchSteps: List<AgentInitialMessageDispatchStep> = emptyList()
    private set

  var initialMessageDispatchStepIndex: Int = 0
    private set

  val initialComposedMessage: String?
    get() = currentPendingInitialMessageStep()?.text

  var initialMessageToken: String? = null
    private set

  var initialMessageSent: Boolean = false
    private set

  private var initialMessageDispatchInFlight: AgentChatInitialMessageDispatch? = null

  @TestOnly
  internal constructor(
    projectPath: String,
    threadIdentity: String,
    shellCommand: List<String>,
    shellEnvVariables: Map<String, String> = emptyMap(),
    threadId: String,
    threadTitle: String,
    subAgentId: String?,
    threadActivity: AgentThreadActivity = AgentThreadActivity.READY,
    projectHash: String = "",
    initialMessageTimeoutPolicy: AgentInitialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK,
  ) : this(
    fileSystem = createStandaloneAgentChatVirtualFileSystemForTest(),
    resolution = AgentChatTabResolution.Resolved(AgentChatTabSnapshot.create(
      projectHash = projectHash,
      projectPath = projectPath,
      threadIdentity = threadIdentity,
      threadId = threadId,
      threadTitle = threadTitle,
      subAgentId = subAgentId,
      shellCommand = shellCommand,
      shellEnvVariables = shellEnvVariables,
      threadActivity = threadActivity,
      initialMessageTimeoutPolicy = initialMessageTimeoutPolicy,
    ))
  )

  init {
    updateFromResolution(resolution)
    isWritable = false
  }

  override fun getFileSystem(): VirtualFileSystem = fileSystem

  override fun getPath(): String = key.toPath()

  fun matches(threadIdentity: String, subAgentId: String?): Boolean {
    return this.threadIdentity == threadIdentity && this.subAgentId == subAgentId
  }

  fun updateThreadTitle(threadTitle: String): Boolean {
    val resolvedTitle = resolveThreadTitle(threadTitle)
    if (this.threadTitle == resolvedTitle) {
      LOG.debug {
        "Skipped tab title update(identity=$threadIdentity, subAgentId=$subAgentId): unchanged title=$resolvedTitle"
      }
      return false
    }

    val oldTitle = this.threadTitle
    this.threadTitle = resolvedTitle
    LOG.debug {
      "Updated tab title(identity=$threadIdentity, subAgentId=$subAgentId): oldTitle=$oldTitle newTitle=$resolvedTitle"
    }
    return true
  }

  fun updateThreadActivity(threadActivity: AgentThreadActivity): Boolean {
    if (this.threadActivity == threadActivity) {
      LOG.debug {
        "Skipped tab activity update(identity=$threadIdentity, subAgentId=$subAgentId): unchanged activity=$threadActivity"
      }
      return false
    }

    val oldActivity = this.threadActivity
    this.threadActivity = threadActivity
    LOG.debug {
      "Updated tab activity(identity=$threadIdentity, subAgentId=$subAgentId): oldActivity=$oldActivity newActivity=$threadActivity"
    }
    return true
  }

  fun updateCommandAndThreadId(shellCommand: List<String>, shellEnvVariables: Map<String, String>, threadId: String) {
    this.shellCommand = shellCommand
    this.shellEnvVariables = shellEnvVariables
    this.threadId = threadId
  }

  @Synchronized
  fun setStartupLaunchSpecOverride(launchSpec: AgentSessionTerminalLaunchSpec) {
    startupLaunchSpecOverride = AgentSessionTerminalLaunchSpec(
      command = launchSpec.command,
      envVariables = shellEnvVariables + launchSpec.envVariables,
    )
  }

  @Synchronized
  fun consumeStartupLaunchSpec(): AgentSessionTerminalLaunchSpec {
    val startupLaunchSpec = startupLaunchSpecOverride
    startupLaunchSpecOverride = null
    return startupLaunchSpec ?: AgentSessionTerminalLaunchSpec(
      command = shellCommand,
      envVariables = shellEnvVariables,
    )
  }

  fun updatePendingMetadata(
    pendingCreatedAtMs: Long?,
    pendingFirstInputAtMs: Long?,
    pendingLaunchMode: String?,
  ): Boolean {
    if (
      this.pendingCreatedAtMs == pendingCreatedAtMs &&
      this.pendingFirstInputAtMs == pendingFirstInputAtMs &&
      this.pendingLaunchMode == pendingLaunchMode
    ) {
      return false
    }
    this.pendingCreatedAtMs = pendingCreatedAtMs
    this.pendingFirstInputAtMs = pendingFirstInputAtMs
    this.pendingLaunchMode = pendingLaunchMode
    return true
  }

  fun updateNewThreadRebindRequestedAtMs(newThreadRebindRequestedAtMs: Long?): Boolean {
    if (this.newThreadRebindRequestedAtMs == newThreadRebindRequestedAtMs) {
      return false
    }
    this.newThreadRebindRequestedAtMs = newThreadRebindRequestedAtMs
    return true
  }

  @Synchronized
  fun updateInitialMessageMetadata(
    initialMessageDispatchSteps: List<AgentInitialMessageDispatchStep>,
    initialMessageDispatchStepIndex: Int,
    initialMessageToken: String?,
    initialMessageSent: Boolean,
  ): Boolean {
    val normalizedSteps = initialMessageDispatchSteps.filter { step -> step.text.isNotBlank() }
    val normalizedStepIndex = initialMessageDispatchStepIndex.coerceIn(0, normalizedSteps.size)
    if (
      this.initialMessageDispatchSteps == normalizedSteps &&
      this.initialMessageDispatchStepIndex == normalizedStepIndex &&
      this.initialMessageToken == initialMessageToken &&
      this.initialMessageSent == initialMessageSent
    ) {
      return false
    }
    this.initialMessageDispatchSteps = normalizedSteps
    this.initialMessageDispatchStepIndex = normalizedStepIndex
    this.initialMessageToken = initialMessageToken
    this.initialMessageSent = initialMessageSent
    initialMessageDispatchInFlight = null
    return true
  }

  @Synchronized
  fun updateInitialMessageMetadata(
    initialComposedMessage: String?,
    initialMessageToken: String?,
    initialMessageSent: Boolean,
    initialMessageTimeoutPolicy: AgentInitialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK,
  ): Boolean {
    val steps = initialComposedMessage
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?.let { message -> listOf(AgentInitialMessageDispatchStep(text = message, timeoutPolicy = initialMessageTimeoutPolicy)) }
      .orEmpty()
    val stepIndex = if (steps.isEmpty() || !initialMessageSent) 0 else steps.size
    return updateInitialMessageMetadata(
      initialMessageDispatchSteps = steps,
      initialMessageDispatchStepIndex = stepIndex,
      initialMessageToken = initialMessageToken,
      initialMessageSent = initialMessageSent,
    )
  }

  @Synchronized
  fun hasPendingInitialMessageForDispatch(): Boolean {
    return currentPendingInitialMessageStep() != null
  }

  @Synchronized
  fun shouldDelayInitialMessageOnReadinessTimeout(): Boolean {
    return currentPendingInitialMessageStep()?.timeoutPolicy == AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS
  }

  @Synchronized
  fun acquireInitialMessageDispatch(): AgentChatInitialMessageDispatch? {
    val stepIndex = initialMessageDispatchStepIndex
    val currentStep = currentPendingInitialMessageStep() ?: return null
    val message = currentStep.text.trim()
    if (message.isEmpty()) {
      return null
    }
    val token = initialMessageToken
    val inFlight = initialMessageDispatchInFlight
    if (inFlight != null && inFlight.message == message && inFlight.token == token && inFlight.stepIndex == stepIndex) {
      return null
    }
    return AgentChatInitialMessageDispatch(
      message = message,
      token = token,
      stepIndex = stepIndex,
      completionPolicy = currentStep.completionPolicy,
    ).also {
      initialMessageDispatchInFlight = it
    }
  }

  @Synchronized
  fun completeInitialMessageDispatch(dispatch: AgentChatInitialMessageDispatch): Boolean {
    if (initialMessageDispatchInFlight !== dispatch) {
      return false
    }
    val currentStep = currentPendingInitialMessageStep()
    val currentMessage = currentStep?.text?.trim().orEmpty()
    if (
      currentMessage.isEmpty() ||
      initialMessageSent ||
      initialMessageToken != dispatch.token ||
      currentMessage != dispatch.message ||
      initialMessageDispatchStepIndex != dispatch.stepIndex
    ) {
      initialMessageDispatchInFlight = null
      return false
    }
    initialMessageDispatchStepIndex += 1
    initialMessageSent = initialMessageDispatchStepIndex >= initialMessageDispatchSteps.size
    initialMessageDispatchInFlight = null
    return true
  }

  @Synchronized
  fun cancelInitialMessageDispatch(dispatch: AgentChatInitialMessageDispatch) {
    if (initialMessageDispatchInFlight === dispatch) {
      initialMessageDispatchInFlight = null
    }
  }

  @Synchronized
  private fun currentPendingInitialMessageStep(): AgentInitialMessageDispatchStep? {
    if (initialMessageSent) {
      return null
    }
    return initialMessageDispatchSteps.getOrNull(initialMessageDispatchStepIndex)
  }

  fun markPendingFirstInputAtMsIfAbsent(timestampMs: Long): Boolean {
    if (!isPendingThread) {
      return false
    }
    if (pendingFirstInputAtMs != null) {
      return false
    }
    pendingFirstInputAtMs = timestampMs
    return true
  }

  fun rebindPendingThread(
    threadIdentity: String,
    shellCommand: List<String>,
    shellEnvVariables: Map<String, String>,
    threadId: String,
    threadTitle: String,
    threadActivity: AgentThreadActivity,
  ): Boolean {
    return rebindThread(
      threadIdentity = threadIdentity,
      shellCommand = shellCommand,
      shellEnvVariables = shellEnvVariables,
      threadId = threadId,
      threadTitle = threadTitle,
      threadActivity = threadActivity,
      clearPendingMetadata = true,
    )
  }

  fun rebindConcreteThread(
    threadIdentity: String,
    shellCommand: List<String>,
    shellEnvVariables: Map<String, String>,
    threadId: String,
    threadTitle: String,
    threadActivity: AgentThreadActivity,
  ): Boolean {
    if (isPendingThread || newThreadRebindRequestedAtMs == null) {
      return false
    }
    return rebindThread(
      threadIdentity = threadIdentity,
      shellCommand = shellCommand,
      shellEnvVariables = shellEnvVariables,
      threadId = threadId,
      threadTitle = threadTitle,
      threadActivity = threadActivity,
      clearPendingMetadata = false,
    )
  }

  private fun rebindThread(
    threadIdentity: String,
    shellCommand: List<String>,
    shellEnvVariables: Map<String, String>,
    threadId: String,
    threadTitle: String,
    threadActivity: AgentThreadActivity,
    clearPendingMetadata: Boolean,
  ): Boolean {
    var changed = false
    if (this.threadIdentity != threadIdentity) {
      this.threadIdentity = threadIdentity
      updateThreadCoordinates()
      changed = true
    }
    if (this.shellCommand != shellCommand || this.shellEnvVariables != shellEnvVariables || this.threadId != threadId) {
      updateCommandAndThreadId(shellCommand = shellCommand, shellEnvVariables = shellEnvVariables, threadId = threadId)
      changed = true
    }
    if (updateThreadTitle(threadTitle)) {
      changed = true
    }
    if (updateThreadActivity(threadActivity)) {
      changed = true
    }
    if (
      clearPendingMetadata &&
      updatePendingMetadata(pendingCreatedAtMs = null, pendingFirstInputAtMs = null, pendingLaunchMode = null)
    ) {
      changed = true
    }
    if (updateNewThreadRebindRequestedAtMs(newThreadRebindRequestedAtMs = null)) {
      changed = true
    }
    if (updateInitialMessageMetadata(
        initialMessageDispatchSteps = emptyList(),
        initialMessageDispatchStepIndex = 0,
        initialMessageToken = null,
        initialMessageSent = false,
      )) {
      changed = true
    }

    if (changed) {
      LOG.debug {
        "Rebound tab(identity=$threadIdentity, subAgentId=$subAgentId, threadId=$threadId)"
      }
    }
    return changed
  }

  internal fun updateFromResolution(resolution: AgentChatTabResolution) {
    when (resolution) {
      is AgentChatTabResolution.Resolved -> updateFromSnapshot(resolution.snapshot)
      is AgentChatTabResolution.Unresolved -> Unit
    }
  }

  private fun updateFromSnapshot(snapshot: AgentChatTabSnapshot) {
    if (snapshot.identity.threadIdentity.isNotBlank() || snapshot.identity.projectPath.isNotBlank()) {
      projectHash = snapshot.identity.projectHash
      projectPath = snapshot.identity.projectPath
      threadIdentity = snapshot.identity.threadIdentity
      subAgentId = snapshot.identity.subAgentId
      updateThreadCoordinates()
    }
    if (
      snapshot.runtime.threadId.isNotBlank() ||
      snapshot.runtime.shellCommand.isNotEmpty() ||
      snapshot.runtime.shellEnvVariables.isNotEmpty()
    ) {
      updateCommandAndThreadId(
        shellCommand = snapshot.runtime.shellCommand,
        shellEnvVariables = snapshot.runtime.shellEnvVariables,
        threadId = snapshot.runtime.threadId,
      )
    }
    if (snapshot.runtime.threadTitle.isNotBlank()) {
      updateThreadTitle(snapshot.runtime.threadTitle)
    }
    updateThreadActivity(snapshot.runtime.threadActivity)
    updatePendingMetadata(
      pendingCreatedAtMs = snapshot.runtime.pendingCreatedAtMs,
      pendingFirstInputAtMs = snapshot.runtime.pendingFirstInputAtMs,
      pendingLaunchMode = snapshot.runtime.pendingLaunchMode,
    )
    updateNewThreadRebindRequestedAtMs(snapshot.runtime.newThreadRebindRequestedAtMs)
    updateInitialMessageMetadata(
      initialMessageDispatchSteps = snapshot.runtime.initialMessageDispatchSteps,
      initialMessageDispatchStepIndex = snapshot.runtime.initialMessageDispatchStepIndex,
      initialMessageToken = snapshot.runtime.initialMessageToken,
      initialMessageSent = snapshot.runtime.initialMessageSent,
    )
  }

  private fun updateThreadCoordinates() {
    val coordinates = resolveAgentChatThreadCoordinates(threadIdentity)
    provider = coordinates?.provider
    sessionId = coordinates?.sessionId.orEmpty()
    isPendingThread = coordinates?.isPending ?: false
  }

  internal fun toSnapshot(): AgentChatTabSnapshot {
    return AgentChatTabSnapshot(
      tabKey = key,
      identity = AgentChatTabIdentity(
        projectHash = projectHash,
        projectPath = projectPath,
        threadIdentity = threadIdentity,
        subAgentId = subAgentId,
      ),
      runtime = AgentChatTabRuntime(
        threadId = threadId,
        threadTitle = threadTitle,
        shellCommand = shellCommand,
        shellEnvVariables = shellEnvVariables,
        threadActivity = threadActivity,
        pendingCreatedAtMs = pendingCreatedAtMs,
        pendingFirstInputAtMs = pendingFirstInputAtMs,
        pendingLaunchMode = pendingLaunchMode,
        newThreadRebindRequestedAtMs = newThreadRebindRequestedAtMs,
        initialMessageDispatchSteps = initialMessageDispatchSteps,
        initialMessageDispatchStepIndex = initialMessageDispatchStepIndex,
        initialMessageToken = initialMessageToken,
        initialMessageSent = initialMessageSent,
      ),
    )
  }
}

internal class AgentChatInitialMessageDispatch internal constructor(
  val message: String,
  val token: String?,
  val stepIndex: Int,
  val completionPolicy: AgentInitialMessageDispatchCompletionPolicy,
)

private fun resolveFileName(tabKey: String): String {
  return "chat-$tabKey"
}

private fun resolveThreadTitle(threadTitle: String): String {
  return threadTitle.takeIf { it.isNotBlank() } ?: AgentChatBundle.message("chat.filetype.name")
}
