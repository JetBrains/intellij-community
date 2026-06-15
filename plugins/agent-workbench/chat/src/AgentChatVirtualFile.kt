// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly

private class AgentChatVirtualFileLog

private val LOG = logger<AgentChatVirtualFileLog>()

enum class AgentChatDeferredStartPhase {
  WAITING,
  READY_TO_START,
  SUCCESS_NO_START,
  FAILURE_NO_START,
}

data class AgentChatDeferredStartState(
  @JvmField val phase: AgentChatDeferredStartPhase,
  @JvmField val title: @Nls String,
  @JvmField val message: @Nls String? = null,
)

internal class AgentChatVirtualFile internal constructor(
  private val fileSystem: AgentChatVirtualFileSystem,
  resolution: AgentChatTabResolution,
) : LightVirtualFile(resolveFileName(resolution.tabKey.value)), EditorHistoryManager.IncludeInEditorHistoryFile {
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

  @Volatile
  private var startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec? = null

  @Volatile
  private var restoreOnRestart: Boolean = true

  @Volatile
  private var startupIntent: AgentChatStartupIntent? = null

  private var suppressInitialMessageDispatchOnStartup: Boolean = false

  var threadId: String = ""
    private set

  @NlsSafe
  var bootstrapThreadTitle: String = resolveThreadTitle("")
    private set

  var bootstrapThreadActivity: AgentThreadActivity = AgentThreadActivity.READY
    private set

  @get:NlsSafe
  val threadTitle: String
    get() = resolveAgentChatThreadPresentation(this).title

  val threadActivity: AgentThreadActivity
    get() = resolveAgentChatThreadPresentation(this).activity

  var pendingCreatedAtMs: Long? = null
    private set

  var pendingFirstInputAtMs: Long? = null
    private set

  var pendingLaunchMode: String? = null
    private set

  var launchMode: String? = null
    private set

  var generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO
    private set

  var newThreadRebindRequestedAtMs: Long? = null
    private set

  var initialMessageDispatchSteps: List<AgentInitialMessageDispatchStep> = emptyList()
    private set

  var initialMessageDispatchStepIndex: Int = 0
    private set

  val initialComposedMessage: String?
    get() = initialMessageDispatchSteps
      .asSequence()
      .drop(initialMessageDispatchStepIndex)
      .firstOrNull { step -> step.action == AgentInitialMessageDispatchAction.SEND_TEXT && step.text.isNotBlank() }
      ?.text

  var initialMessageToken: String? = null
    private set

  var initialMessageSent: Boolean = false
    private set

  private var initialMessageDispatchInFlight: AgentChatInitialMessageDispatch? = null

  @Volatile
  var deferredStartState: AgentChatDeferredStartState? = null
    private set

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
      threadActivity = threadActivity,
      initialMessageTimeoutPolicy = initialMessageTimeoutPolicy,
    ))
  ) {
    if (shellCommand.isNotEmpty() || shellEnvVariables.isNotEmpty()) {
      setStartupLaunchSpecOverride(AgentSessionTerminalLaunchSpec(command = shellCommand, envVariables = shellEnvVariables))
    }
  }

  init {
    updateFromResolution(resolution)
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
    isWritable = false
  }

  override fun getFileSystem(): VirtualFileSystem = fileSystem

  override fun getPath(): String = key.toPath()

  override fun isPersistedInEditorHistory(): Boolean = false

  fun matches(threadIdentity: String, subAgentId: String?): Boolean {
    return this.threadIdentity == threadIdentity && this.subAgentId == subAgentId
  }

  fun updateBootstrapThreadTitle(threadTitle: String): Boolean {
    val resolvedTitle = resolveThreadTitle(threadTitle)
    if (bootstrapThreadTitle == resolvedTitle) {
      LOG.debug {
        "Skipped chat bootstrap title update(identity=$threadIdentity, subAgentId=$subAgentId): unchanged title=$resolvedTitle"
      }
      return false
    }

    val oldTitle = bootstrapThreadTitle
    bootstrapThreadTitle = resolvedTitle
    LOG.debug {
      "Updated chat bootstrap title(identity=$threadIdentity, subAgentId=$subAgentId): oldTitle=$oldTitle newTitle=$resolvedTitle"
    }
    return true
  }

  fun updateBootstrapThreadActivity(threadActivity: AgentThreadActivity): Boolean {
    if (bootstrapThreadActivity == threadActivity) {
      LOG.debug {
        "Skipped chat bootstrap activity update(identity=$threadIdentity, subAgentId=$subAgentId): unchanged activity=$threadActivity"
      }
      return false
    }

    val oldActivity = bootstrapThreadActivity
    bootstrapThreadActivity = threadActivity
    LOG.debug {
      "Updated chat bootstrap activity(identity=$threadIdentity, subAgentId=$subAgentId): oldActivity=$oldActivity newActivity=$threadActivity"
    }
    return true
  }

  fun updateThreadId(threadId: String) {
    this.threadId = threadId
  }

  fun updateDeferredStartState(deferredStartState: AgentChatDeferredStartState?): Boolean {
    if (this.deferredStartState == deferredStartState) {
      return false
    }
    this.deferredStartState = deferredStartState
    return true
  }

  fun participatesInPendingThreadLifecycle(): Boolean {
    return isPendingThread && when (deferredStartState?.phase) {
      AgentChatDeferredStartPhase.SUCCESS_NO_START,
      AgentChatDeferredStartPhase.FAILURE_NO_START,
        -> false

      else -> true
    }
  }

  @Synchronized
  fun setStartupLaunchSpecOverride(
    launchSpec: AgentSessionTerminalLaunchSpec,
    suppressInitialMessageDispatch: Boolean = false,
  ) {
    startupLaunchSpecOverride = launchSpec
    suppressInitialMessageDispatchOnStartup = suppressInitialMessageDispatch
  }

  @Synchronized
  fun consumeStartupLaunchSpecOverride(): AgentSessionTerminalLaunchSpec? {
    val startupLaunchSpec = startupLaunchSpecOverride
    startupLaunchSpecOverride = null
    if (startupLaunchSpec == null) {
      suppressInitialMessageDispatchOnStartup = false
    }
    return startupLaunchSpec
  }

  @Synchronized
  fun consumeSuppressInitialMessageDispatchOnStartup(): Boolean {
    val suppress = suppressInitialMessageDispatchOnStartup
    suppressInitialMessageDispatchOnStartup = false
    return suppress
  }

  fun shouldRestoreOnRestart(): Boolean = restoreOnRestart

  fun updateRestoreOnRestart(restoreOnRestart: Boolean): Boolean {
    if (this.restoreOnRestart == restoreOnRestart) {
      return false
    }
    this.restoreOnRestart = restoreOnRestart
    return true
  }

  @Synchronized
  fun startupIntent(): AgentChatStartupIntent? = startupIntent

  @Synchronized
  fun updateStartupIntent(startupIntent: AgentChatStartupIntent?): Boolean {
    if (this.startupIntent == startupIntent) {
      return false
    }
    this.startupIntent = startupIntent
    return true
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

  fun updateLaunchMode(launchMode: String?): Boolean {
    val normalized = normalizeAgentChatLaunchMode(launchMode)
    if (this.launchMode == normalized) {
      return false
    }
    this.launchMode = normalized
    return true
  }

  fun updateGenerationSettings(generationSettings: AgentPromptGenerationSettings): Boolean {
    if (this.generationSettings == generationSettings) {
      return false
    }
    this.generationSettings = generationSettings
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
    val normalizedSteps = initialMessageDispatchSteps.filter(AgentInitialMessageDispatchStep::isDispatchable)
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
  fun clearInitialMessageDispatchMetadata(): Boolean {
    return updateInitialMessageMetadata(
      initialMessageDispatchSteps = emptyList(),
      initialMessageDispatchStepIndex = 0,
      initialMessageToken = null,
      initialMessageSent = false,
    )
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
    if (currentStep.action == AgentInitialMessageDispatchAction.SEND_TEXT && message.isEmpty()) {
      return null
    }
    val token = initialMessageToken
    val inFlight = initialMessageDispatchInFlight
    if (
      inFlight != null &&
      inFlight.action == currentStep.action &&
      inFlight.message == message &&
      inFlight.token == token &&
      inFlight.stepIndex == stepIndex
    ) {
      return null
    }
    return AgentChatInitialMessageDispatch(
      action = currentStep.action,
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
      currentStep == null ||
      initialMessageSent ||
      currentStep.action != dispatch.action ||
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
    threadId: String,
    threadTitle: String,
    threadActivity: AgentThreadActivity,
  ): Boolean {
    return rebindThread(
      threadIdentity = threadIdentity,
      threadId = threadId,
      threadTitle = threadTitle,
      threadActivity = threadActivity,
      clearPendingMetadata = true,
    )
  }

  fun rebindConcreteThread(
    threadIdentity: String,
    threadId: String,
    threadTitle: String,
    threadActivity: AgentThreadActivity,
  ): Boolean {
    return !isPendingThread && newThreadRebindRequestedAtMs != null && rebindThread(
      threadIdentity = threadIdentity,
      threadId = threadId,
      threadTitle = threadTitle,
      threadActivity = threadActivity,
      clearPendingMetadata = false,
    )
  }

  private fun rebindThread(
    threadIdentity: String,
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
    if (this.threadId != threadId) {
      updateThreadId(threadId)
      changed = true
    }
    if (updateBootstrapThreadTitle(threadTitle)) {
      changed = true
    }
    if (updateBootstrapThreadActivity(threadActivity)) {
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
    if (currentPendingInitialMessageStep() == null) {
      if (updateInitialMessageMetadata(
          initialMessageDispatchSteps = emptyList(),
          initialMessageDispatchStepIndex = 0,
          initialMessageToken = null,
          initialMessageSent = false,
        )) {
        changed = true
      }
    }
    if (updateStartupIntent(null)) {
      changed = true
    }
    if (updateRestoreOnRestart(true)) {
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
    if (snapshot.runtime.threadId.isNotBlank()) {
      updateThreadId(snapshot.runtime.threadId)
    }
    if (snapshot.runtime.threadTitle.isNotBlank()) {
      updateBootstrapThreadTitle(snapshot.runtime.threadTitle)
    }
    updateBootstrapThreadActivity(snapshot.runtime.threadActivity)
    updatePendingMetadata(
      pendingCreatedAtMs = snapshot.runtime.pendingCreatedAtMs,
      pendingFirstInputAtMs = snapshot.runtime.pendingFirstInputAtMs,
      pendingLaunchMode = snapshot.runtime.pendingLaunchMode,
    )
    updateLaunchMode(snapshot.runtime.launchMode)
    updateGenerationSettings(snapshot.runtime.generationSettings)
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
        threadTitle = bootstrapThreadTitle,
        threadActivity = bootstrapThreadActivity,
        pendingCreatedAtMs = pendingCreatedAtMs,
        pendingFirstInputAtMs = pendingFirstInputAtMs,
        pendingLaunchMode = pendingLaunchMode,
        launchMode = launchMode,
        generationSettings = generationSettings,
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
  val action: AgentInitialMessageDispatchAction,
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
