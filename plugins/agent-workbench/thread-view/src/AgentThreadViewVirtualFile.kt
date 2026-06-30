// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryChannel
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryStatus
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptRecord
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.ai.agent.sessions.core.providers.AgentTerminalPromptDispatch
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly

private class AgentThreadViewVirtualFileLog

private val LOG = logger<AgentThreadViewVirtualFileLog>()

enum class AgentThreadViewDeferredStartPhase {
  WAITING,
  READY_TO_START,
  SUCCESS_NO_START,
  FAILURE_NO_START,
}

data class AgentThreadViewDeferredStartState(
  @JvmField val phase: AgentThreadViewDeferredStartPhase,
  @JvmField val title: @Nls String,
  @JvmField val message: @Nls String? = null,
)

internal class AgentThreadViewVirtualFile internal constructor(
  private val fileSystem: AgentThreadViewVirtualFileSystem,
  resolution: AgentThreadViewTabResolution,
) : LightVirtualFile(resolveFileName(resolution.tabKey.value)), EditorHistoryManager.IncludeInEditorHistoryFile, AgentThreadViewBehaviorFile {
  private val key: AgentThreadViewTabKey = resolution.tabKey

  val tabKey: String
    get() = key.value

  var projectHash: String = ""
    private set

  var projectPath: String = ""
    private set

  var projectDirectory: String? = null
    private set

  var threadIdentity: String = ""
    private set

  override var provider: AgentSessionProvider? = null
    private set

  var sessionId: String = ""
    private set

  override var isPendingThread: Boolean = false
    private set

  override var subAgentId: String? = null
    private set

  @Volatile
  private var startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec? = null

  @Volatile
  private var restoreOnRestart: Boolean = true

  @Volatile
  private var startupIntent: AgentThreadViewStartupIntent? = null

  private var suppressInitialMessageDispatchOnStartup: Boolean = false

  var threadId: String = ""
    private set

  @NlsSafe
  var bootstrapThreadTitle: String = resolveThreadTitle("")
    private set

  var bootstrapThreadActivityReport: AgentThreadActivityReport = AgentThreadActivityReport.READY
    private set

  val bootstrapThreadActivity: AgentThreadActivity
    get() = bootstrapThreadActivityReport.rowActivity

  @get:NlsSafe
  val threadTitle: String
    get() = resolveAgentThreadViewThreadPresentation(this).title

  override val threadActivity: AgentThreadActivity
    get() = resolveAgentThreadViewThreadPresentation(this).activityReport.rowActivity

  var pendingCreatedAtMs: Long? = null
    private set

  override var pendingFirstInputAtMs: Long? = null
    private set

  var pendingLaunchMode: String? = null
    private set

  var launchMode: String? = null
    private set

  var launchProfileId: String? = null
    private set

  var launchTargetId: String? = null
    private set

  var surfaceId: String? = null
    private set

  var generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO
    private set

  var newThreadRebindRequestedAtMs: Long? = null
    private set

  private var initialPromptRecord: AgentInitialPromptRecord? = null

  private var terminalPromptDispatch: AgentTerminalPromptDispatch? = null

  val initialMessageDispatchSteps: List<AgentInitialMessageDispatchStep>
    get() = terminalPromptDispatch?.steps.orEmpty()

  val initialMessageDispatchStepIndex: Int
    get() = terminalPromptDispatch?.stepIndex ?: 0

  val initialComposedMessage: String?
    get() = initialPromptRecord?.message

  val initialMessageToken: String?
    get() = initialPromptRecord?.token

  override val initialMessageMode: AgentInitialMessageMode?
    get() = initialPromptRecord?.mode

  val initialMessageSent: Boolean
    get() = initialPromptRecord?.deliveryStatus == AgentInitialPromptDeliveryStatus.DELIVERED

  private var initialMessageDispatchInFlight: AgentThreadViewInitialMessageDispatch? = null

  @Volatile
  var deferredStartState: AgentThreadViewDeferredStartState? = null
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
    fileSystem = createStandaloneAgentThreadViewVirtualFileSystemForTest(),
    resolution = AgentThreadViewTabResolution.Resolved(AgentThreadViewTabSnapshot.create(
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
        "Skipped threadView bootstrap title update(identity=$threadIdentity, subAgentId=$subAgentId): unchanged title=$resolvedTitle"
      }
      return false
    }

    val oldTitle = bootstrapThreadTitle
    bootstrapThreadTitle = resolvedTitle
    LOG.debug {
      "Updated threadView bootstrap title(identity=$threadIdentity, subAgentId=$subAgentId): oldTitle=$oldTitle newTitle=$resolvedTitle"
    }
    return true
  }

  fun updateBootstrapThreadActivity(threadActivity: AgentThreadActivity): Boolean {
    return updateBootstrapThreadActivityReport(AgentThreadActivityReport(threadActivity))
  }

  fun updateBootstrapThreadActivityReport(activityReport: AgentThreadActivityReport): Boolean {
    if (bootstrapThreadActivityReport == activityReport) {
      LOG.debug {
        "Skipped threadView bootstrap activity update(identity=$threadIdentity, subAgentId=$subAgentId): unchanged activity=$activityReport"
      }
      return false
    }

    val oldActivity = bootstrapThreadActivityReport
    bootstrapThreadActivityReport = activityReport
    LOG.debug {
      "Updated threadView bootstrap activity(identity=$threadIdentity, subAgentId=$subAgentId): oldActivity=$oldActivity newActivity=$activityReport"
    }
    return true
  }

  fun updateThreadId(threadId: String) {
    this.threadId = threadId
  }

  fun updateDeferredStartState(deferredStartState: AgentThreadViewDeferredStartState?): Boolean {
    if (this.deferredStartState == deferredStartState) {
      return false
    }
    this.deferredStartState = deferredStartState
    return true
  }

  fun participatesInPendingThreadLifecycle(): Boolean {
    return isPendingThread && when (deferredStartState?.phase) {
      AgentThreadViewDeferredStartPhase.SUCCESS_NO_START,
      AgentThreadViewDeferredStartPhase.FAILURE_NO_START,
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
  fun startupIntent(): AgentThreadViewStartupIntent? = startupIntent

  @Synchronized
  fun updateStartupIntent(startupIntent: AgentThreadViewStartupIntent?): Boolean {
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
    val normalized = normalizeAgentThreadViewLaunchMode(launchMode)
    if (this.launchMode == normalized) {
      return false
    }
    this.launchMode = normalized
    return true
  }

  fun updateLaunchProfileId(launchProfileId: String?): Boolean {
    val normalized = launchProfileId?.trim()?.takeIf(String::isNotEmpty)
    if (this.launchProfileId == normalized) {
      return false
    }
    this.launchProfileId = normalized
    return true
  }

  fun updateLaunchTargetId(launchTargetId: String?): Boolean {
    val normalized = launchTargetId?.trim()?.takeIf(String::isNotEmpty)
    if (this.launchTargetId == normalized) {
      return false
    }
    this.launchTargetId = normalized
    return true
  }

  fun updateSurfaceId(surfaceId: String?): Boolean {
    val normalized = normalizeAgentThreadViewSurfaceId(surfaceId)
    if (this.surfaceId == normalized) {
      return false
    }
    this.surfaceId = normalized
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
  fun updateInitialPromptDelivery(
    promptRecord: AgentInitialPromptRecord?,
    terminalDispatch: AgentTerminalPromptDispatch?,
  ): Boolean {
    val normalizedTerminalDispatch = terminalDispatch
      ?.normalized()
      ?.takeIf { promptRecord?.deliveryStatus != AgentInitialPromptDeliveryStatus.DELIVERED }
    if (this.initialPromptRecord == promptRecord && this.terminalPromptDispatch == normalizedTerminalDispatch) {
      return false
    }
    this.initialPromptRecord = promptRecord
    this.terminalPromptDispatch = normalizedTerminalDispatch
    initialMessageDispatchInFlight = null
    return true
  }

  @Synchronized
  fun updateInitialMessageMetadata(
    initialMessageDispatchSteps: List<AgentInitialMessageDispatchStep>,
    initialMessageDispatchStepIndex: Int,
    initialMessageToken: String?,
    initialMessageSent: Boolean,
  ): Boolean {
    val terminalDispatch = AgentTerminalPromptDispatch(
      steps = initialMessageDispatchSteps,
      stepIndex = initialMessageDispatchStepIndex,
    ).normalized()
    return updateInitialPromptDelivery(
      promptRecord = buildInitialPromptRecord(
        steps = terminalDispatch?.steps.orEmpty(),
        token = initialMessageToken,
        sent = initialMessageSent,
      ),
      terminalDispatch = terminalDispatch.takeUnless { initialMessageSent },
    )
  }

  @Synchronized
  fun clearInitialMessageDispatchMetadata(): Boolean {
    return updateInitialPromptDelivery(promptRecord = null, terminalDispatch = null)
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
    return updateInitialMessageMetadata(
      initialMessageDispatchSteps = steps,
      initialMessageDispatchStepIndex = 0,
      initialMessageToken = initialMessageToken,
      initialMessageSent = initialMessageSent,
    )
  }

  @Synchronized
  fun markInitialPromptDelivered(deliveryChannel: AgentInitialPromptDeliveryChannel): Boolean {
    val promptRecord = initialPromptRecord ?: return false
    return updateInitialPromptDelivery(
      promptRecord = promptRecord.copy(
        deliveryStatus = AgentInitialPromptDeliveryStatus.DELIVERED,
        deliveryChannel = deliveryChannel,
      ),
      terminalDispatch = null,
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
  fun acquireInitialMessageDispatch(): AgentThreadViewInitialMessageDispatch? {
    val terminalDispatch = terminalPromptDispatch ?: return null
    val stepIndex = terminalDispatch.stepIndex
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
    return AgentThreadViewInitialMessageDispatch(
      action = currentStep.action,
      message = message,
      token = token,
      stepIndex = stepIndex,
    ).also {
      initialMessageDispatchInFlight = it
    }
  }

  @Synchronized
  fun completeInitialMessageDispatch(dispatch: AgentThreadViewInitialMessageDispatch): Boolean {
    if (initialMessageDispatchInFlight !== dispatch) {
      return false
    }
    val terminalDispatch = terminalPromptDispatch
    val currentStep = currentPendingInitialMessageStep()
    val currentMessage = currentStep?.text?.trim().orEmpty()
    if (
      terminalDispatch == null ||
      currentStep == null ||
      initialMessageSent ||
      currentStep.action != dispatch.action ||
      initialMessageToken != dispatch.token ||
      currentMessage != dispatch.message ||
      terminalDispatch.stepIndex != dispatch.stepIndex
    ) {
      initialMessageDispatchInFlight = null
      return false
    }
    val nextStepIndex = terminalDispatch.stepIndex + 1
    if (nextStepIndex >= terminalDispatch.steps.size) {
      initialPromptRecord = initialPromptRecord?.copy(
        deliveryStatus = AgentInitialPromptDeliveryStatus.DELIVERED,
        deliveryChannel = dispatch.deliveryChannel(),
      )
      terminalPromptDispatch = null
    }
    else {
      terminalPromptDispatch = terminalDispatch.copy(stepIndex = nextStepIndex)
    }
    initialMessageDispatchInFlight = null
    return true
  }

  @Synchronized
  fun cancelInitialMessageDispatch(dispatch: AgentThreadViewInitialMessageDispatch) {
    if (initialMessageDispatchInFlight === dispatch) {
      initialMessageDispatchInFlight = null
    }
  }

  @Synchronized
  fun rewindInitialMessageDispatch(dispatch: AgentThreadViewInitialMessageDispatch): Boolean {
    if (initialMessageDispatchInFlight !== dispatch) {
      return false
    }
    val terminalDispatch = terminalPromptDispatch ?: run {
      initialMessageDispatchInFlight = null
      return false
    }
    terminalPromptDispatch = terminalDispatch.copy(stepIndex = 0)
    initialMessageDispatchInFlight = null
    return true
  }

  @Synchronized
  private fun currentPendingInitialMessageStep(): AgentInitialMessageDispatchStep? {
    if (initialMessageSent) {
      return null
    }
    val terminalDispatch = terminalPromptDispatch ?: return null
    return terminalDispatch.steps.getOrNull(terminalDispatch.stepIndex)
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
    return rebindPendingThread(
      threadIdentity = threadIdentity,
      threadId = threadId,
      threadTitle = threadTitle,
      threadActivityReport = AgentThreadActivityReport(threadActivity),
    )
  }

  fun rebindPendingThread(
    threadIdentity: String,
    threadId: String,
    threadTitle: String,
    threadActivityReport: AgentThreadActivityReport,
  ): Boolean {
    return rebindThread(
      threadIdentity = threadIdentity,
      threadId = threadId,
      threadTitle = threadTitle,
      threadActivityReport = threadActivityReport,
      clearPendingMetadata = true,
    )
  }

  fun rebindConcreteThread(
    threadIdentity: String,
    threadId: String,
    threadTitle: String,
    threadActivity: AgentThreadActivity,
  ): Boolean {
    return rebindConcreteThread(
      threadIdentity = threadIdentity,
      threadId = threadId,
      threadTitle = threadTitle,
      threadActivityReport = AgentThreadActivityReport(threadActivity),
    )
  }

  fun rebindConcreteThread(
    threadIdentity: String,
    threadId: String,
    threadTitle: String,
    threadActivityReport: AgentThreadActivityReport,
  ): Boolean {
    return !isPendingThread && newThreadRebindRequestedAtMs != null && rebindThread(
      threadIdentity = threadIdentity,
      threadId = threadId,
      threadTitle = threadTitle,
      threadActivityReport = threadActivityReport,
      clearPendingMetadata = false,
    )
  }

  private fun rebindThread(
    threadIdentity: String,
    threadId: String,
    threadTitle: String,
    threadActivityReport: AgentThreadActivityReport,
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
    if (updateBootstrapThreadActivityReport(threadActivityReport)) {
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

  internal fun updateFromResolution(resolution: AgentThreadViewTabResolution) {
    when (resolution) {
      is AgentThreadViewTabResolution.Resolved -> updateFromSnapshot(resolution.snapshot)
      is AgentThreadViewTabResolution.Unresolved -> Unit
    }
  }

  private fun updateFromSnapshot(snapshot: AgentThreadViewTabSnapshot) {
    if (snapshot.identity.threadIdentity.isNotBlank() || snapshot.identity.projectPath.isNotBlank()) {
      projectHash = snapshot.identity.projectHash
      projectPath = snapshot.identity.projectPath
      projectDirectory = snapshot.identity.projectDirectory
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
    updateLaunchProfileId(snapshot.runtime.launchProfileId)
    updateLaunchTargetId(snapshot.runtime.launchTargetId)
    updateSurfaceId(snapshot.runtime.surfaceId)
    updateGenerationSettings(snapshot.runtime.generationSettings)
    updateNewThreadRebindRequestedAtMs(snapshot.runtime.newThreadRebindRequestedAtMs)
    updateInitialPromptDelivery(
      promptRecord = snapshot.runtime.initialPromptRecord,
      terminalDispatch = snapshot.runtime.terminalPromptDispatch,
    )
  }

  private fun updateThreadCoordinates() {
    val coordinates = resolveAgentThreadViewThreadCoordinates(threadIdentity)
    provider = coordinates?.provider
    sessionId = coordinates?.sessionId.orEmpty()
    isPendingThread = coordinates?.isPending ?: false
  }

  internal fun toSnapshot(): AgentThreadViewTabSnapshot {
    return AgentThreadViewTabSnapshot(
      tabKey = key,
      identity = AgentThreadViewTabIdentity(
        projectHash = projectHash,
        projectPath = projectPath,
        projectDirectory = projectDirectory,
        threadIdentity = threadIdentity,
        subAgentId = subAgentId,
      ),
      runtime = AgentThreadViewTabRuntime(
        threadId = threadId,
        threadTitle = bootstrapThreadTitle,
        threadActivity = bootstrapThreadActivity,
        pendingCreatedAtMs = pendingCreatedAtMs,
        pendingFirstInputAtMs = pendingFirstInputAtMs,
        pendingLaunchMode = pendingLaunchMode,
        launchMode = launchMode,
        launchProfileId = launchProfileId,
        launchTargetId = launchTargetId,
        surfaceId = surfaceId,
        generationSettings = generationSettings,
        newThreadRebindRequestedAtMs = newThreadRebindRequestedAtMs,
        initialPromptRecord = initialPromptRecord,
        terminalPromptDispatch = terminalPromptDispatch,
      ),
    )
  }
}

private fun buildInitialPromptRecord(
  steps: List<AgentInitialMessageDispatchStep>,
  token: String?,
  sent: Boolean,
): AgentInitialPromptRecord? {
  val message = steps.lastOrNull { step ->
    step.recordsPrompt &&
    step.action == AgentInitialMessageDispatchAction.SEND_TEXT &&
    step.text.isNotBlank()
  }?.text ?: return null
  return AgentInitialPromptRecord(
    message = message,
    token = token,
    deliveryStatus = if (sent) AgentInitialPromptDeliveryStatus.DELIVERED else AgentInitialPromptDeliveryStatus.PENDING,
    deliveryChannel = AgentInitialPromptDeliveryChannel.TERMINAL,
  )
}

internal class AgentThreadViewInitialMessageDispatch internal constructor(
  override val action: AgentInitialMessageDispatchAction,
  override val message: String,
  val token: String?,
  override val stepIndex: Int,
) : AgentThreadViewInitialMessageDispatchContext

private fun AgentThreadViewInitialMessageDispatch.deliveryChannel(): AgentInitialPromptDeliveryChannel {
  return when (action) {
    AgentInitialMessageDispatchAction.SEND_TEXT -> AgentInitialPromptDeliveryChannel.TERMINAL
    AgentInitialMessageDispatchAction.PROVIDER -> AgentInitialPromptDeliveryChannel.APP_SERVER
  }
}

private fun resolveFileName(tabKey: String): String {
  return "threadView-$tabKey"
}

private fun resolveThreadTitle(threadTitle: String): String {
  return threadTitle.takeIf { it.isNotBlank() } ?: AgentThreadViewBundle.message("thread.view.filetype.name")
}
