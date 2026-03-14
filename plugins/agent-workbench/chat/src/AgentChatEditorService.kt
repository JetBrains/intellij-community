// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchSpecs
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

private class AgentChatEditorServiceLog

private val LOG = logger<AgentChatEditorServiceLog>()
private val fileEditorProviderOverrideForTests: AtomicReference<FileEditorProvider?> = AtomicReference(null)

private data class AgentChatScopedRefreshSignal(
  val provider: AgentSessionProvider,
  val projectPaths: Set<String>,
)

private object AgentChatScopedRefreshSignalBus {
  private val signalFlow = MutableSharedFlow<AgentChatScopedRefreshSignal>(extraBufferCapacity = 64)

  fun signal(provider: AgentSessionProvider, projectPath: String): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(projectPath)
    return normalizedPath.isNotBlank() && signalFlow.tryEmit(
      AgentChatScopedRefreshSignal(provider = provider, projectPaths = setOf(normalizedPath))
    )
  }

  fun signals(provider: AgentSessionProvider): Flow<Set<String>> {
    return signalFlow.asSharedFlow()
      .filter { signal -> signal.provider == provider }
      .map { signal -> signal.projectPaths }
  }
}

data class AgentChatTabRebindTarget(
  @JvmField val projectPath: String,
  val provider: AgentSessionProvider,
  @JvmField val threadIdentity: String,
  @JvmField val threadId: String,
  @JvmField val threadTitle: String,
  @JvmField val threadActivity: AgentThreadActivity,
)

suspend fun openChat(
  project: Project,
  projectPath: String,
  threadIdentity: String,
  shellCommand: List<String>,
  shellEnvVariables: Map<String, String> = emptyMap(),
  threadId: String,
  threadTitle: String,
  subAgentId: String?,
  threadActivity: AgentThreadActivity = AgentThreadActivity.READY,
  pendingCreatedAtMs: Long? = null,
  pendingFirstInputAtMs: Long? = null,
  pendingLaunchMode: String? = null,
  initialMessageDispatchPlan: AgentInitialMessageDispatchPlan = AgentInitialMessageDispatchPlan.EMPTY,
) {
  val manager = FileEditorManagerEx.getInstanceExAsync(project)

  val tabKey = AgentChatTabKey.fromIdentity(
    AgentChatTabIdentity(
      projectHash = project.locationHash,
      projectPath = projectPath,
      threadIdentity = threadIdentity,
      subAgentId = subAgentId,
    )
  )
  val existing = findExistingChatByTabKey(manager.openFiles, tabKey.value)
                 ?: findExistingChat(manager.openFiles, threadIdentity, subAgentId)
  val startupOverrideForNewTab = if (existing == null) initialMessageDispatchPlan.startupLaunchSpecOverride else null
  val snapshotInitialMessageDispatchSteps = if (startupOverrideForNewTab != null) emptyList() else initialMessageDispatchPlan.postStartDispatchSteps
  val snapshotInitialMessageToken = if (startupOverrideForNewTab != null) null else initialMessageDispatchPlan.initialMessageToken
  val snapshotInitialMessageSent = false
  val snapshot = AgentChatTabSnapshot.create(
    projectHash = project.locationHash,
    projectPath = projectPath,
    threadIdentity = threadIdentity,
    threadId = threadId,
    threadTitle = threadTitle,
    subAgentId = subAgentId,
    shellCommand = shellCommand,
    threadActivity = threadActivity,
    pendingCreatedAtMs = pendingCreatedAtMs,
    pendingFirstInputAtMs = pendingFirstInputAtMs,
    pendingLaunchMode = pendingLaunchMode,
    newThreadRebindRequestedAtMs = existing?.newThreadRebindRequestedAtMs,
    initialMessageDispatchSteps = snapshotInitialMessageDispatchSteps,
    initialMessageToken = snapshotInitialMessageToken,
    initialMessageSent = snapshotInitialMessageSent,
  )
  val file = existing ?: fileSystem.getOrCreateFile(snapshot)
  if (existing != null) {
    existing.updateCommandAndThreadId(shellCommand = shellCommand, threadId = threadId)
    val titleUpdated = existing.updateThreadTitle(threadTitle)
    val activityUpdated = existing.updateThreadActivity(threadActivity)
    tabsService.upsert(existing.toSnapshot())
    LOG.debug {
      "openChat existing tab update(identity=$threadIdentity, subAgentId=$subAgentId): " +
      "titleUpdated=$titleUpdated, activityUpdated=$activityUpdated, currentName=${existing.name}," +
      " currentTitle=${existing.threadTitle}, currentActivity=${existing.threadActivity}"
    }
    else {
      false
    }
    val initialMessageUpdated = if (
      initialMessageDispatchPlan.postStartDispatchSteps.isNotEmpty() ||
      initialMessageDispatchPlan.initialMessageToken != null
    ) {
      existing.updateInitialMessageMetadata(
        initialMessageDispatchSteps = initialMessageDispatchPlan.postStartDispatchSteps,
        initialMessageDispatchStepIndex = 0,
        initialMessageToken = initialMessageDispatchPlan.initialMessageToken,
        initialMessageSent = false,
      )
    }
    else {
      false
    }
    tabsService.upsert(existing.toSnapshot())
    LOG.debug {
      "openChat existing tab update(identity=$threadIdentity, subAgentId=$subAgentId): " +
      "titleUpdated=$titleUpdated, activityUpdated=$activityUpdated, currentName=${existing.name}," +
      " currentTitle=${existing.threadTitle}, currentActivity=${existing.threadActivity}"
    }
    if (titleUpdated || activityUpdated || pendingUpdated || initialMessageUpdated) {
      manager.updateFilePresentation(existing)
    }
    if (initialMessageUpdated && !existing.initialMessageSent) {
      flushPendingInitialMessageForOpenEditors(manager = manager, file = existing)
    }
  }
  else {
    tabsService.upsert(file.toSnapshot())
    LOG.debug {
      "openChat created new tab(identity=$threadIdentity, subAgentId=$subAgentId, fileName=${file.name}, activity=$threadActivity)"
    }
  }
  if (ApplicationManager.getApplication().isUnitTestMode) {
    val provider = fileEditorProviderOverrideForTests.get()
    if (provider != null) {
      // TestEditorManagerImpl uses FileEditorProvider.KEY for non-text editors and otherwise falls back to doOpenTextEditor.
      file.putUserData(FileEditorProvider.KEY, provider)
    }
  }
  manager.openFile(
    file = file,
    options = FileEditorOpenOptions(requestFocus = true, reuseOpen = true),
  )
  LOG.debug {
    "openChat openFile completed(identity=$threadIdentity, subAgentId=$subAgentId, fileName=${file.name}, activity=$threadActivity)"
  }

  val pendingProvider = pendingProviderForThreadIdentity(threadIdentity)
  if (pendingProvider != null && AgentSessionProviders.find(pendingProvider)?.emitsScopedRefreshSignals == true) {
    notifyAgentChatTerminalOutputForRefresh(provider = pendingProvider, projectPath = projectPath)
  }
}

@Suppress("unused")
suspend fun collectOpenAgentChatProjectPaths(): Set<String> {
  return collectOpenAgentChatProjectPaths(includePendingOnly = false)
}

suspend fun collectOpenPendingAgentChatProjectPaths(): Set<String> {
  return collectOpenAgentChatProjectPaths(includePendingOnly = true)
}

fun notifyAgentChatTerminalOutputForRefresh(provider: AgentSessionProvider, projectPath: String) {
  AgentChatScopedRefreshSignalBus.signal(provider, projectPath)
}

fun agentChatScopedRefreshSignals(provider: AgentSessionProvider): Flow<Set<String>> {
  return AgentChatScopedRefreshSignalBus.signals(provider)
}

fun notifyCodexTerminalOutputForRefresh(projectPath: String) {
  notifyAgentChatTerminalOutputForRefresh(provider = AgentSessionProvider.CODEX, projectPath = projectPath)
}

fun codexScopedRefreshSignals(): Flow<Set<String>> {
  return agentChatScopedRefreshSignals(AgentSessionProvider.CODEX)
}

suspend fun collectOpenPendingAgentChatTabsByPath(
  provider: AgentSessionProvider,
): Map<String, List<AgentChatPendingCodexTabSnapshot>> {
  return collectOpenAgentChatTabsSnapshotOnUi().pendingTabsByPath(provider)
}

suspend fun collectOpenPendingCodexTabsByPath(): Map<String, List<AgentChatPendingCodexTabSnapshot>> {
  return collectOpenPendingAgentChatTabsByPath(AgentSessionProvider.CODEX)
}

suspend fun collectOpenConcreteAgentChatTabsAwaitingNewThreadRebindByPath(
  provider: AgentSessionProvider,
): Map<String, List<AgentChatConcreteCodexTabSnapshot>> {
  return collectOpenAgentChatTabsSnapshotOnUi().concreteTabsAwaitingNewThreadRebindByPath(provider)
}

suspend fun collectOpenConcreteCodexTabsAwaitingNewThreadRebindByPath(): Map<String, List<AgentChatConcreteCodexTabSnapshot>> {
  return collectOpenConcreteAgentChatTabsAwaitingNewThreadRebindByPath(AgentSessionProvider.CODEX)
}

suspend fun collectOpenConcreteAgentChatThreadIdentitiesByPath(): Map<String, Set<String>> {
  return collectOpenAgentChatTabsSnapshotOnUi().concreteThreadIdentitiesByPath()
}

private suspend fun collectOpenAgentChatProjectPaths(includePendingOnly: Boolean): Set<String> {
  return collectOpenAgentChatTabsSnapshotOnUi().projectPaths(includePendingOnly)
}

private data class AgentChatRebindLaunchSpecKey(
  val projectPath: String,
  val provider: AgentSessionProvider,
  val threadId: String,
)

private fun AgentChatTabRebindTarget.toRebindLaunchSpecKey(): AgentChatRebindLaunchSpecKey {
  return AgentChatRebindLaunchSpecKey(
    projectPath = normalizeAgentWorkbenchPath(projectPath),
    provider = provider,
    threadId = threadId,
  )
}

private suspend fun resolveRebindLaunchSpec(target: AgentChatTabRebindTarget): AgentSessionTerminalLaunchSpec? {
  return try {
    AgentSessionLaunchSpecs.resolveResume(
      projectPath = normalizeAgentWorkbenchPath(target.projectPath),
      provider = target.provider,
      sessionId = target.threadId,
    )
  }
  catch (t: Throwable) {
    LOG.warn(
      "Failed to resolve chat rebind launch spec for ${target.provider.value}:${target.projectPath}:${target.threadId}",
      t,
    )
    null
  }
}

private suspend fun resolveRebindLaunchSpecs(
  targets: Sequence<AgentChatTabRebindTarget>,
): Map<AgentChatRebindLaunchSpecKey, AgentSessionTerminalLaunchSpec?> {
  val launchSpecsByTarget = LinkedHashMap<AgentChatRebindLaunchSpecKey, AgentSessionTerminalLaunchSpec?>()
  for (target in targets) {
    val key = target.toRebindLaunchSpecKey()
    if (key !in launchSpecsByTarget) {
      launchSpecsByTarget[key] = resolveRebindLaunchSpec(target)
    }
  }
  return launchSpecsByTarget
}

suspend fun rebindOpenPendingAgentChatTabs(
  provider: AgentSessionProvider,
  requestsByProjectPath: Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
): AgentChatPendingCodexTabRebindReport {
  if (requestsByProjectPath.isEmpty()) {
    return emptyPendingCodexTabRebindReport()
  }

  val tabsService = serviceAsync<AgentChatTabsService>()
  val updatedSnapshots = ArrayList<AgentChatTabSnapshot>()
  var reboundTabs: Int
  var updatedPresentations: Int
  withContext(Dispatchers.UI) {
    val managerByFile = LinkedHashMap<AgentChatVirtualFile, LinkedHashSet<FileEditorManagerEx>>()
    val openConcreteIdentitiesByPath = LinkedHashMap<String, LinkedHashSet<String>>()
    val pendingFilesByPath = LinkedHashMap<String, MutableList<AgentChatVirtualFile>>()

  val launchSpecsByTarget = resolveRebindLaunchSpecs(
    normalizedRequestsByPath.values.asSequence().flatten().map { request -> request.target }
  )
  val tabsService = serviceAsync<AgentChatTabsService>()
  val report = withContext(Dispatchers.UI) {
    val openTabsSnapshot = collectOpenAgentChatTabsSnapshot()

    var reboundBindings = 0
    val changedFiles = LinkedHashSet<AgentChatVirtualFile>()
    val outcomesByPath = LinkedHashMap<String, MutableList<AgentChatPendingCodexTabRebindOutcome>>()
    for ((normalizedPath, requests) in normalizedRequestsByPath) {
      val outcomes = outcomesByPath.computeIfAbsent(normalizedPath) { ArrayList(requests.size) }
      for (request in requests) {
        val pendingFile = openTabsSnapshot.findPendingFile(provider, normalizedPath, request.pendingTabKey)
        if (pendingFile == null) {
          outcomes.add(
            AgentChatPendingCodexTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatPendingCodexTabRebindStatus.PENDING_TAB_NOT_OPEN,
              reboundFiles = 0,
            )
          )
          continue
        }

        val managers = openTabsSnapshot.managersFor(pendingFile)
        if (managers.isEmpty()) {
          outcomes.add(
            AgentChatPendingCodexTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatPendingCodexTabRebindStatus.PENDING_TAB_NOT_OPEN,
              reboundFiles = 0,
            )
          )
          continue
        }

        if (
          !isPendingThreadIdentityForProvider(pendingFile.threadIdentity, provider) ||
          pendingFile.threadIdentity != request.pendingThreadIdentity
        ) {
          outcomes.add(
            AgentChatPendingCodexTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatPendingCodexTabRebindStatus.INVALID_PENDING_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        val targetIdentityAlreadyOpen = openTabsSnapshot.isConcreteThreadIdentityOpenInAnyManager(
          normalizedPath = normalizedPath,
          managers = managers,
          threadIdentity = request.target.threadIdentity,
        )
        if (targetIdentityAlreadyOpen) {
          outcomes.add(
            AgentChatPendingCodexTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatPendingCodexTabRebindStatus.TARGET_ALREADY_OPEN,
              reboundFiles = 0,
            )
          )
          continue
        }

        val launchSpec = launchSpecsByTarget[request.target.toRebindLaunchSpecKey()]
        if (launchSpec == null) {
          outcomes.add(
            AgentChatPendingCodexTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatPendingCodexTabRebindStatus.INVALID_PENDING_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        val changed = pendingFile.rebindPendingThread(
          threadIdentity = request.target.threadIdentity,
          shellCommand = launchSpec.command,
          shellEnvVariables = launchSpec.envVariables,
          threadId = request.target.threadId,
          threadTitle = request.target.threadTitle,
          threadActivity = request.target.threadActivity,
        )
        if (!changed) {
          outcomes.add(
            AgentChatPendingCodexTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatPendingCodexTabRebindStatus.INVALID_PENDING_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        reboundBindings++
        tabsService.upsert(pendingFile.toSnapshot())
        changedFiles.add(pendingFile)
        openTabsSnapshot.recordConcreteThreadIdentityOpen(normalizedPath, managers, request.target.threadIdentity)
        outcomes.add(
          AgentChatPendingCodexTabRebindOutcome(
            projectPath = normalizedPath,
            request = request,
            status = AgentChatPendingCodexTabRebindStatus.REBOUND,
            reboundFiles = 1,
          )
        )
      }
    }

    var updatedPresentations = 0
    for (changedFile in changedFiles) {
      val managers = openTabsSnapshot.managersFor(changedFile)
      for (manager in managers) {
        manager.updateFilePresentation(changedFile)
        updatedPresentations++
      }
    }

    val requestedBindings = normalizedRequestsByPath.values.sumOf { it.size }
    AgentChatPendingCodexTabRebindReport(
      requestedBindings = requestedBindings,
      reboundBindings = reboundBindings,
      reboundFiles = changedFiles.size,
      updatedPresentations = updatedPresentations,
      outcomesByPath = outcomesByPath,
    )
  }
  LOG.debug {
    "rebindOpenAgentChatPendingTabs reboundTabs=$reboundTabs, updatedPresentations=$updatedPresentations," +
    " requestedPaths=${targetsByProjectPath.size}"
  }
  return report
}

suspend fun rebindOpenPendingCodexTabs(
  requestsByProjectPath: Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
): AgentChatPendingCodexTabRebindReport {
  return rebindOpenPendingAgentChatTabs(AgentSessionProvider.CODEX, requestsByProjectPath)
}

suspend fun rebindOpenConcreteAgentChatTabs(
  provider: AgentSessionProvider,
  requestsByProjectPath: Map<String, List<AgentChatConcreteCodexTabRebindRequest>>,
): AgentChatConcreteCodexTabRebindReport {
  if (requestsByProjectPath.isEmpty()) {
    return emptyConcreteCodexTabRebindReport()
  }

  val normalizedRequestsByPath = normalizePathToListMap(requestsByProjectPath)
  if (normalizedRequestsByPath.isEmpty()) {
    return emptyConcreteCodexTabRebindReport()
  }

  val launchSpecsByTarget = resolveRebindLaunchSpecs(
    normalizedRequestsByPath.values.asSequence().flatten().map { request -> request.target }
  )
  val tabsService = serviceAsync<AgentChatTabsService>()
  val report = withContext(Dispatchers.UI) {
    val openTabsSnapshot = collectOpenAgentChatTabsSnapshot()

    var reboundBindings = 0
    val changedFiles = LinkedHashSet<AgentChatVirtualFile>()
    val outcomesByPath = LinkedHashMap<String, MutableList<AgentChatConcreteCodexTabRebindOutcome>>()
    for ((normalizedPath, requests) in normalizedRequestsByPath) {
      val outcomes = outcomesByPath.computeIfAbsent(normalizedPath) { ArrayList(requests.size) }
      for (request in requests) {
        val concreteFile = openTabsSnapshot.findConcreteFile(provider, normalizedPath, request.tabKey)
        if (concreteFile == null) {
          outcomes.add(
            AgentChatConcreteCodexTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatConcreteCodexTabRebindStatus.CONCRETE_TAB_NOT_OPEN,
              reboundFiles = 0,
            )
          )
          continue
        }

        val managers = openTabsSnapshot.managersFor(concreteFile)
        if (managers.isEmpty()) {
          outcomes.add(
            AgentChatConcreteCodexTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatConcreteCodexTabRebindStatus.CONCRETE_TAB_NOT_OPEN,
              reboundFiles = 0,
            )
          )
          continue
        }

        if (
          concreteFile.provider != provider ||
          concreteFile.isPendingThread ||
          concreteFile.subAgentId != null ||
          concreteFile.threadIdentity != request.currentThreadIdentity ||
          concreteFile.newThreadRebindRequestedAtMs != request.newThreadRebindRequestedAtMs
        ) {
          outcomes.add(
            AgentChatConcreteCodexTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatConcreteCodexTabRebindStatus.INVALID_CONCRETE_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        val targetIdentityAlreadyOpen = openTabsSnapshot.isConcreteThreadIdentityOpenInAnyManager(
          normalizedPath = normalizedPath,
          managers = managers,
          threadIdentity = request.target.threadIdentity,
        )
        if (targetIdentityAlreadyOpen) {
          outcomes.add(
            AgentChatConcreteCodexTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatConcreteCodexTabRebindStatus.TARGET_ALREADY_OPEN,
              reboundFiles = 0,
            )
          )
          continue
        }

        val launchSpec = launchSpecsByTarget[request.target.toRebindLaunchSpecKey()]
        if (launchSpec == null) {
          outcomes.add(
            AgentChatConcreteCodexTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatConcreteCodexTabRebindStatus.INVALID_CONCRETE_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        val previousIdentity = concreteFile.threadIdentity
        val changed = concreteFile.rebindConcreteThread(
          threadIdentity = request.target.threadIdentity,
          shellCommand = launchSpec.command,
          shellEnvVariables = launchSpec.envVariables,
          threadId = request.target.threadId,
          threadTitle = request.target.threadTitle,
          threadActivity = request.target.threadActivity,
        )
        if (!changed) {
          outcomes.add(
            AgentChatConcreteCodexTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatConcreteCodexTabRebindStatus.INVALID_CONCRETE_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        reboundBindings++
        tabsService.upsert(concreteFile.toSnapshot())
        changedFiles.add(concreteFile)
        openTabsSnapshot.replaceConcreteThreadIdentity(
          normalizedPath = normalizedPath,
          managers = managers,
          previousIdentity = previousIdentity,
          threadIdentity = request.target.threadIdentity,
        )
        outcomes.add(
          AgentChatConcreteCodexTabRebindOutcome(
            projectPath = normalizedPath,
            request = request,
            status = AgentChatConcreteCodexTabRebindStatus.REBOUND,
            reboundFiles = 1,
          )
        )
      }
    }

    var updatedPresentations = 0
    for (changedFile in changedFiles) {
      val managers = openTabsSnapshot.managersFor(changedFile)
      for (manager in managers) {
        manager.updateFilePresentation(changedFile)
        updatedPresentations++
      }
    }

    val requestedBindings = normalizedRequestsByPath.values.sumOf { it.size }
    AgentChatConcreteCodexTabRebindReport(
      requestedBindings = requestedBindings,
      reboundBindings = reboundBindings,
      reboundFiles = changedFiles.size,
      updatedPresentations = updatedPresentations,
      outcomesByPath = outcomesByPath,
    )
  }
  LOG.debug {
    "rebindOpenConcreteCodexTabs requestedBindings=${report.requestedBindings}, reboundBindings=${report.reboundBindings}, " +
    "reboundFiles=${report.reboundFiles}, updatedPresentations=${report.updatedPresentations}, paths=${report.outcomesByPath.size}"
  }
  return report
}

suspend fun rebindOpenConcreteCodexTabs(
  requestsByProjectPath: Map<String, List<AgentChatConcreteCodexTabRebindRequest>>,
): AgentChatConcreteCodexTabRebindReport {
  return rebindOpenConcreteAgentChatTabs(AgentSessionProvider.CODEX, requestsByProjectPath)
}

fun clearOpenConcreteAgentChatNewThreadRebindAnchors(
  provider: AgentSessionProvider,
  tabsByProjectPath: Map<String, List<AgentChatConcreteCodexTabSnapshot>>,
): Int {
  if (tabsByProjectPath.isEmpty()) {
    return 0
  }

  val normalizedTabsByPath = normalizePathToListMap(tabsByProjectPath)
  if (normalizedTabsByPath.isEmpty()) {
    return 0
  }

  val tabsService = service<AgentChatTabsService>()
  val openTabsSnapshot = collectOpenAgentChatTabsSnapshot()

  var cleared = 0
  for ((normalizedPath, tabs) in normalizedTabsByPath) {
    for (tab in tabs) {
      val concreteFile = openTabsSnapshot.findConcreteFile(provider, normalizedPath, tab.tabKey) ?: continue
      if (
        concreteFile.threadIdentity != tab.currentThreadIdentity ||
        concreteFile.newThreadRebindRequestedAtMs != tab.newThreadRebindRequestedAtMs
      ) {
        continue
      }
      if (!concreteFile.updateNewThreadRebindRequestedAtMs(newThreadRebindRequestedAtMs = null)) {
        continue
      }
      tabsService.upsert(concreteFile.toSnapshot())
      cleared++
    }
  }
  return cleared
}

@Suppress("unused")
fun clearOpenConcreteCodexNewThreadRebindAnchors(
  tabsByProjectPath: Map<String, List<AgentChatConcreteCodexTabSnapshot>>,
): Int {
  return clearOpenConcreteAgentChatNewThreadRebindAnchors(AgentSessionProvider.CODEX, tabsByProjectPath)
}

suspend fun updateOpenAgentChatTabPresentation(
  titleByPathAndThreadIdentity: Map<Pair<String, String>, String>,
  activityByPathAndThreadIdentity: Map<Pair<String, String>, AgentThreadActivity>,
): Int {
  if (titleByPathAndThreadIdentity.isEmpty() && activityByPathAndThreadIdentity.isEmpty()) {
    return 0
  }

  val updatedSnapshots = ArrayList<AgentChatTabSnapshot>()
  var updatedTabs: Int
  var updatedPresentations: Int
  val tabsService = serviceAsync<AgentChatTabsService>()
  withContext(Dispatchers.UI) {
    val openTabsSnapshot = collectOpenAgentChatTabsSnapshot()
    val changedFiles = LinkedHashSet<AgentChatVirtualFile>()

    for (chatFile in openTabsSnapshot.files()) {
      val key = normalizeAgentWorkbenchPath(chatFile.projectPath) to chatFile.threadIdentity
      val targetTitle = normalizedTitlesByPathAndThreadIdentity[key]
      val targetActivity = normalizedActivitiesByPathAndThreadIdentity[key]
      if (targetTitle == null && targetActivity == null) {
        continue
      }

      var presentationUpdated = false
      if (targetTitle != null && chatFile.subAgentId == null && chatFile.updateThreadTitle(targetTitle)) {
        presentationUpdated = true
      }
      if (targetActivity != null && chatFile.updateThreadActivity(targetActivity)) {
        presentationUpdated = true
      }

      if (presentationUpdated) {
        updatedSnapshots.add(chatFile.toSnapshot())
        changedFiles.add(chatFile)
      }
    }

    updatedTabs = changedFiles.size
    updatedPresentations = 0
    for (chatFile in changedFiles) {
      val managers = openTabsSnapshot.managersFor(chatFile)
      for (manager in managers) {
        manager.updateFilePresentation(chatFile)
        updatedPresentations++
      }
    }
  }

  if (updatedSnapshots.isNotEmpty()) {
    withContext(Dispatchers.IO) {
      for (snapshot in updatedSnapshots) {
        tabsService.upsert(snapshot)
      }
    }
  }

  LOG.debug {
    "updateOpenAgentChatTabPresentation updatedTabs=$updatedTabs, updatedPresentations=$updatedPresentations," +
    " requestedTitles=${titleByPathAndThreadIdentity.size}, requestedActivities=${activityByPathAndThreadIdentity.size}"
  }
  return updatedTabs
}

@Suppress("unused")
suspend fun updateOpenAgentChatTabTitles(
  titleByPathAndThreadIdentity: Map<Pair<String, String>, String>,
): Int {
  return updateOpenAgentChatTabPresentation(
    titleByPathAndThreadIdentity = titleByPathAndThreadIdentity,
    activityByPathAndThreadIdentity = emptyMap(),
  )
}

suspend fun collectSelectedChatThreadIdentity(): Pair<AgentSessionProvider, String>? = withContext(Dispatchers.EDT) {
  collectOpenAgentChatTabsSnapshot().selectedChatThreadIdentity
}

private fun <T> normalizePathToListMap(pathToValues: Map<String, List<T>>): LinkedHashMap<String, List<T>> {
  val normalizedPathToValues = LinkedHashMap<String, List<T>>()
  for ((projectPath, values) in pathToValues) {
    if (values.isEmpty()) {
      continue
    }
    normalizedPathToValues[normalizeAgentWorkbenchPath(projectPath)] = values
  }
  return normalizedPathToValues
}

private fun <T> normalizePathAndThreadIdentityMap(
  valuesByPathAndThreadIdentity: Map<Pair<String, String>, T>,
): LinkedHashMap<Pair<String, String>, T> {
  val normalizedValuesByPathAndThreadIdentity = LinkedHashMap<Pair<String, String>, T>()
  for ((key, value) in valuesByPathAndThreadIdentity) {
    normalizedValuesByPathAndThreadIdentity[normalizeAgentWorkbenchPath(key.first) to key.second] = value
  }
  return normalizedValuesByPathAndThreadIdentity
}

private fun emptyPendingCodexTabRebindReport(): AgentChatPendingCodexTabRebindReport {
  return AgentChatPendingCodexTabRebindReport(
    requestedBindings = 0,
    reboundBindings = 0,
    reboundFiles = 0,
    updatedPresentations = 0,
    outcomesByPath = emptyMap(),
  )
}

private fun emptyConcreteCodexTabRebindReport(): AgentChatConcreteCodexTabRebindReport {
  return AgentChatConcreteCodexTabRebindReport(
    requestedBindings = 0,
    reboundBindings = 0,
    reboundFiles = 0,
    updatedPresentations = 0,
    outcomesByPath = emptyMap(),
  )
}

private fun findExistingChat(
  openFiles: Array<VirtualFile>,
  threadIdentity: String,
  subAgentId: String?,
): AgentChatVirtualFile? {
  for (openFile in openFiles) {
    val chatFile = openFile as? AgentChatVirtualFile ?: continue
    if (chatFile.matches(threadIdentity, subAgentId)) {
      return chatFile
    }
  }
  return null
}

private fun findExistingChatByTabKey(
  openFiles: Array<VirtualFile>,
  tabKey: String,
): AgentChatVirtualFile? {
  for (openFile in openFiles) {
    val chatFile = openFile as? AgentChatVirtualFile ?: continue
    if (chatFile.tabKey == tabKey) {
      return chatFile
    }
  }
  return null
}

private fun flushPendingInitialMessageForOpenEditors(
  manager: FileEditorManagerEx,
  file: AgentChatVirtualFile,
) {
  manager.getAllEditors(file)
    .filterIsInstance<AgentChatFileEditor>()
    .forEach { editor ->
      editor.flushPendingInitialMessageIfInitialized()
    }
}
