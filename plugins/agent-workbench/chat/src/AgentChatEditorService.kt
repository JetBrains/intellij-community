// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.parseAgentThreadIdentity
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

private class AgentChatEditorServiceLog

private val LOG = logger<AgentChatEditorServiceLog>()
private const val CODEX_PROVIDER_ID = "codex"
private val fileEditorProviderOverrideForTests: AtomicReference<FileEditorProvider?> = AtomicReference(null)

private object CodexScopedRefreshSignalBus {
  private val signalFlow = MutableSharedFlow<Set<String>>(extraBufferCapacity = 64)

  fun signal(projectPath: String): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(projectPath)
    return normalizedPath.isNotBlank() && signalFlow.tryEmit(setOf(normalizedPath))
  }

  fun signals(): Flow<Set<String>> = signalFlow.asSharedFlow()
}

data class AgentChatTabRebindTarget(
  @JvmField val threadIdentity: String,
  @JvmField val threadId: String,
  @JvmField val shellCommand: List<String>,
  @JvmField val shellEnvVariables: Map<String, String> = emptyMap(),
  @JvmField val threadTitle: String,
  @JvmField val threadActivity: AgentThreadActivity,
  @JvmField val threadUpdatedAt: Long = 0L,
)

data class AgentChatPendingCodexTabSnapshot(
  @JvmField val projectPath: String,
  @JvmField val pendingTabKey: String,
  @JvmField val pendingThreadIdentity: String,
  @JvmField val pendingCreatedAtMs: Long?,
  @JvmField val pendingFirstInputAtMs: Long?,
  @JvmField val pendingLaunchMode: String?,
)

data class AgentChatConcreteCodexTabSnapshot(
  @JvmField val projectPath: String,
  @JvmField val tabKey: String,
  @JvmField val currentThreadIdentity: String,
  @JvmField val newThreadRebindRequestedAtMs: Long,
)

data class AgentChatPendingCodexTabRebindRequest(
  @JvmField val pendingTabKey: String,
  @JvmField val pendingThreadIdentity: String,
  @JvmField val target: AgentChatTabRebindTarget,
)

data class AgentChatConcreteCodexTabRebindRequest(
  @JvmField val tabKey: String,
  @JvmField val currentThreadIdentity: String,
  @JvmField val newThreadRebindRequestedAtMs: Long,
  @JvmField val target: AgentChatTabRebindTarget,
)

enum class AgentChatPendingCodexTabRebindStatus {
  REBOUND,
  PENDING_TAB_NOT_OPEN,
  INVALID_PENDING_TAB,
  TARGET_ALREADY_OPEN,
}

data class AgentChatPendingCodexTabRebindOutcome(
  @JvmField val projectPath: String,
  @JvmField val request: AgentChatPendingCodexTabRebindRequest,
  @JvmField val status: AgentChatPendingCodexTabRebindStatus,
  @JvmField val reboundFiles: Int,
)

data class AgentChatPendingCodexTabRebindReport(
  @JvmField val requestedBindings: Int,
  @JvmField val reboundBindings: Int,
  @JvmField val reboundFiles: Int,
  @JvmField val updatedPresentations: Int,
  @JvmField val outcomesByPath: Map<String, List<AgentChatPendingCodexTabRebindOutcome>>,
)

enum class AgentChatConcreteCodexTabRebindStatus {
  REBOUND,
  CONCRETE_TAB_NOT_OPEN,
  INVALID_CONCRETE_TAB,
  TARGET_ALREADY_OPEN,
}

data class AgentChatConcreteCodexTabRebindOutcome(
  @JvmField val projectPath: String,
  @JvmField val request: AgentChatConcreteCodexTabRebindRequest,
  @JvmField val status: AgentChatConcreteCodexTabRebindStatus,
  @JvmField val reboundFiles: Int,
)

data class AgentChatConcreteCodexTabRebindReport(
  @JvmField val requestedBindings: Int,
  @JvmField val reboundBindings: Int,
  @JvmField val reboundFiles: Int,
  @JvmField val updatedPresentations: Int,
  @JvmField val outcomesByPath: Map<String, List<AgentChatConcreteCodexTabRebindOutcome>>,
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
  val snapshotInitialComposedMessage = if (startupOverrideForNewTab != null) null else initialMessageDispatchPlan.initialComposedMessage
  val snapshotInitialMessageToken = if (startupOverrideForNewTab != null) null else initialMessageDispatchPlan.initialMessageToken
  val snapshotInitialMessageSent = false
  val snapshotInitialMessageTimeoutPolicy = if (startupOverrideForNewTab != null) {
    AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK
  }
  else {
    initialMessageDispatchPlan.initialMessageTimeoutPolicy
  }
  val snapshot = AgentChatTabSnapshot.create(
    projectHash = project.locationHash,
    projectPath = projectPath,
    threadIdentity = threadIdentity,
    threadId = threadId,
    threadTitle = threadTitle,
    subAgentId = subAgentId,
    shellCommand = shellCommand,
    shellEnvVariables = shellEnvVariables,
    threadActivity = threadActivity,
    pendingCreatedAtMs = pendingCreatedAtMs,
    pendingFirstInputAtMs = pendingFirstInputAtMs,
    pendingLaunchMode = pendingLaunchMode,
    newThreadRebindRequestedAtMs = existing?.newThreadRebindRequestedAtMs,
    initialComposedMessage = snapshotInitialComposedMessage,
    initialMessageToken = snapshotInitialMessageToken,
    initialMessageSent = snapshotInitialMessageSent,
    initialMessageTimeoutPolicy = snapshotInitialMessageTimeoutPolicy,
  )
  LOG.debug {
    "openChat(project=${project.name}, path=$projectPath, identity=$threadIdentity, " +
    "subAgentId=$subAgentId, existing=${existing != null}, title=$threadTitle)"
  }
  val tabsService = serviceAsync<AgentChatTabsService>()
  val fileSystem = agentChatVirtualFileSystemAsync()

  val file = existing ?: fileSystem.getOrCreateFile(snapshot)
  if (existing != null) {
    existing.updateFromResolution(AgentChatTabResolution.Resolved(snapshot))
    existing.updateCommandAndThreadId(shellCommand = shellCommand, shellEnvVariables = shellEnvVariables, threadId = threadId)
    val titleUpdated = existing.updateThreadTitle(threadTitle)
    val activityUpdated = existing.updateThreadActivity(threadActivity)
    val pendingUpdated = if (
      pendingCreatedAtMs != null ||
      pendingFirstInputAtMs != null ||
      pendingLaunchMode != null
    ) {
      existing.updatePendingMetadata(
        pendingCreatedAtMs = pendingCreatedAtMs,
        pendingFirstInputAtMs = pendingFirstInputAtMs,
        pendingLaunchMode = pendingLaunchMode,
      )
    }
    else {
      false
    }
    val initialMessageUpdated = if (
      initialMessageDispatchPlan.initialComposedMessage != null ||
      initialMessageDispatchPlan.initialMessageToken != null ||
      initialMessageDispatchPlan.initialMessageTimeoutPolicy != AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK
    ) {
      existing.updateInitialMessageMetadata(
        initialComposedMessage = initialMessageDispatchPlan.initialComposedMessage,
        initialMessageToken = initialMessageDispatchPlan.initialMessageToken,
        initialMessageSent = false,
        initialMessageTimeoutPolicy = initialMessageDispatchPlan.initialMessageTimeoutPolicy,
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
    if (startupOverrideForNewTab != null) {
      file.setStartupLaunchSpecOverride(startupOverrideForNewTab)
    }
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

  if (isPendingCodexThreadIdentity(threadIdentity)) {
    notifyCodexTerminalOutputForRefresh(projectPath)
  }
}

suspend fun collectOpenAgentChatProjectPaths(): Set<String> {
  return collectOpenAgentChatProjectPaths(includePendingOnly = false)
}

suspend fun collectOpenPendingAgentChatProjectPaths(): Set<String> {
  return collectOpenAgentChatProjectPaths(includePendingOnly = true)
}

fun notifyCodexTerminalOutputForRefresh(projectPath: String) {
  CodexScopedRefreshSignalBus.signal(projectPath)
}

fun codexScopedRefreshSignals(): Flow<Set<String>> {
  return CodexScopedRefreshSignalBus.signals()
}

suspend fun collectOpenPendingCodexTabsByPath(): Map<String, List<AgentChatPendingCodexTabSnapshot>> = withContext(Dispatchers.UI) {
  val result = LinkedHashMap<String, MutableList<AgentChatPendingCodexTabSnapshot>>()
  for (project in ProjectManager.getInstance().openProjects) {
    val manager = project.serviceIfCreated<FileEditorManager>() ?: continue
    for (openFile in manager.openFiles) {
      val chatFile = openFile as? AgentChatVirtualFile ?: continue
      if (!isPendingCodexThreadIdentity(chatFile.threadIdentity)) {
        continue
      }
      val normalizedPath = normalizeAgentWorkbenchPath(chatFile.projectPath)
      result.computeIfAbsent(normalizedPath) { ArrayList() }.add(
        AgentChatPendingCodexTabSnapshot(
          projectPath = normalizedPath,
          pendingTabKey = chatFile.tabKey,
          pendingThreadIdentity = chatFile.threadIdentity,
          pendingCreatedAtMs = chatFile.pendingCreatedAtMs,
          pendingFirstInputAtMs = chatFile.pendingFirstInputAtMs,
          pendingLaunchMode = chatFile.pendingLaunchMode,
        )
      )
    }
  }
  result
}

suspend fun collectOpenConcreteCodexTabsAwaitingNewThreadRebindByPath(): Map<String, List<AgentChatConcreteCodexTabSnapshot>> = withContext(Dispatchers.UI) {
  val snapshotsByPathAndTabKey = LinkedHashMap<String, LinkedHashMap<String, AgentChatConcreteCodexTabSnapshot>>()
  for (project in ProjectManager.getInstance().openProjects) {
    val manager = project.serviceIfCreated<FileEditorManager>() ?: continue
    for (openFile in manager.openFiles) {
      val chatFile = openFile as? AgentChatVirtualFile ?: continue
      if (
        chatFile.provider != AgentSessionProvider.CODEX ||
        chatFile.isPendingThread ||
        chatFile.subAgentId != null
      ) {
        continue
      }
      val requestedAtMs = chatFile.newThreadRebindRequestedAtMs ?: continue
      val normalizedPath = normalizeAgentWorkbenchPath(chatFile.projectPath)
      snapshotsByPathAndTabKey
        .computeIfAbsent(normalizedPath) { LinkedHashMap() }
        .putIfAbsent(
          chatFile.tabKey,
          AgentChatConcreteCodexTabSnapshot(
            projectPath = normalizedPath,
            tabKey = chatFile.tabKey,
            currentThreadIdentity = chatFile.threadIdentity,
            newThreadRebindRequestedAtMs = requestedAtMs,
          )
        )
    }
  }
  val result = LinkedHashMap<String, List<AgentChatConcreteCodexTabSnapshot>>(snapshotsByPathAndTabKey.size)
  for ((normalizedPath, snapshotsByTabKey) in snapshotsByPathAndTabKey) {
    if (snapshotsByTabKey.isEmpty()) {
      continue
    }
    result[normalizedPath] = ArrayList(snapshotsByTabKey.values)
  }
  result
}

suspend fun collectOpenConcreteAgentChatThreadIdentitiesByPath(): Map<String, Set<String>> = withContext(Dispatchers.UI) {
  val result = LinkedHashMap<String, LinkedHashSet<String>>()
  for (project in ProjectManager.getInstance().openProjects) {
    val manager = project.serviceIfCreated<FileEditorManager>() ?: continue
    for (openFile in manager.openFiles) {
      val chatFile = openFile as? AgentChatVirtualFile ?: continue
      if (isPendingThreadIdentity(chatFile.threadIdentity)) {
        continue
      }
      val normalizedPath = normalizeAgentWorkbenchPath(chatFile.projectPath)
      result.computeIfAbsent(normalizedPath) { LinkedHashSet() }.add(chatFile.threadIdentity)
    }
  }
  result
}

private suspend fun collectOpenAgentChatProjectPaths(includePendingOnly: Boolean): Set<String> = withContext(Dispatchers.UI) {
  val paths = LinkedHashSet<String>()
  for (project in ProjectManager.getInstance().openProjects) {
    val manager = project.serviceIfCreated<FileEditorManager>() ?: continue
    for (openFile in manager.openFiles) {
      val chatFile = openFile as? AgentChatVirtualFile ?: continue
      if (includePendingOnly && !isPendingCodexThreadIdentity(chatFile.threadIdentity)) {
        continue
      }
      paths.add(normalizeAgentWorkbenchPath(chatFile.projectPath))
    }
  }
  paths
}

fun rebindOpenPendingCodexTabs(
  requestsByProjectPath: Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
): AgentChatPendingCodexTabRebindReport {
  if (requestsByProjectPath.isEmpty()) {
    return emptyPendingCodexTabRebindReport()
  }

  val normalizedRequestsByPath = LinkedHashMap<String, List<AgentChatPendingCodexTabRebindRequest>>()
  for ((projectPath, requests) in requestsByProjectPath) {
    if (requests.isEmpty()) {
      continue
    }
    normalizedRequestsByPath[normalizeAgentWorkbenchPath(projectPath)] = requests
  }
  if (normalizedRequestsByPath.isEmpty()) {
    return emptyPendingCodexTabRebindReport()
  }

  val tabsService = service<AgentChatTabsService>()
  val managerByFile = LinkedHashMap<AgentChatVirtualFile, LinkedHashSet<FileEditorManagerEx>>()
  val openConcreteIdentitiesByPathAndManager = LinkedHashMap<String, LinkedHashMap<FileEditorManagerEx, LinkedHashSet<String>>>()
  val pendingFilesByPathAndTabKey = LinkedHashMap<String, LinkedHashMap<String, AgentChatVirtualFile>>()
  for (project in ProjectManager.getInstance().openProjects) {
    val manager = project.serviceIfCreated<FileEditorManager>() as? FileEditorManagerEx ?: continue
    for (openFile in manager.openFiles) {
      val chatFile = openFile as? AgentChatVirtualFile ?: continue
      val normalizedPath = normalizeAgentWorkbenchPath(chatFile.projectPath)
      managerByFile.computeIfAbsent(chatFile) { LinkedHashSet() }.add(manager)
      if (isPendingCodexThreadIdentity(chatFile.threadIdentity)) {
        pendingFilesByPathAndTabKey
          .computeIfAbsent(normalizedPath) { LinkedHashMap() }
          .putIfAbsent(chatFile.tabKey, chatFile)
      }
      else {
        openConcreteIdentitiesByPathAndManager
          .computeIfAbsent(normalizedPath) { LinkedHashMap() }
          .computeIfAbsent(manager) { LinkedHashSet() }
          .add(chatFile.threadIdentity)
      }
    }
  }

  var reboundBindings = 0
  val changedFiles = LinkedHashSet<AgentChatVirtualFile>()
  val outcomesByPath = LinkedHashMap<String, MutableList<AgentChatPendingCodexTabRebindOutcome>>()
  for ((normalizedPath, requests) in normalizedRequestsByPath) {
    val outcomes = outcomesByPath.computeIfAbsent(normalizedPath) { ArrayList(requests.size) }
    for (request in requests) {
      val pendingFile = pendingFilesByPathAndTabKey[normalizedPath]?.get(request.pendingTabKey)
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

      val managers = managerByFile[pendingFile].orEmpty()
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
        !isPendingCodexThreadIdentity(pendingFile.threadIdentity) ||
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

      val targetIdentityAlreadyOpen = managers.any { manager ->
        request.target.threadIdentity in openConcreteIdentitiesByPathAndManager[normalizedPath]?.get(manager).orEmpty()
      }
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

      val changed = pendingFile.rebindPendingThread(
        threadIdentity = request.target.threadIdentity,
        shellCommand = request.target.shellCommand,
        shellEnvVariables = request.target.shellEnvVariables,
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
      for (manager in managers) {
        openConcreteIdentitiesByPathAndManager
          .computeIfAbsent(normalizedPath) { LinkedHashMap() }
          .computeIfAbsent(manager) { LinkedHashSet() }
          .add(request.target.threadIdentity)
      }
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
    val managers = managerByFile[changedFile].orEmpty()
    for (manager in managers) {
      manager.updateFilePresentation(changedFile)
      updatedPresentations++
    }
  }

  val requestedBindings = normalizedRequestsByPath.values.sumOf { it.size }
  val report = AgentChatPendingCodexTabRebindReport(
    requestedBindings = requestedBindings,
    reboundBindings = reboundBindings,
    reboundFiles = changedFiles.size,
    updatedPresentations = updatedPresentations,
    outcomesByPath = outcomesByPath,
  )
  LOG.debug {
    "rebindOpenPendingCodexTabs requestedBindings=${report.requestedBindings}, reboundBindings=${report.reboundBindings}, " +
    "reboundFiles=${report.reboundFiles}, updatedPresentations=${report.updatedPresentations}, paths=${report.outcomesByPath.size}"
  }
  return report
}

fun rebindOpenConcreteCodexTabs(
  requestsByProjectPath: Map<String, List<AgentChatConcreteCodexTabRebindRequest>>,
): AgentChatConcreteCodexTabRebindReport {
  if (requestsByProjectPath.isEmpty()) {
    return emptyConcreteCodexTabRebindReport()
  }

  val normalizedRequestsByPath = LinkedHashMap<String, List<AgentChatConcreteCodexTabRebindRequest>>()
  for ((projectPath, requests) in requestsByProjectPath) {
    if (requests.isEmpty()) {
      continue
    }
    normalizedRequestsByPath[normalizeAgentWorkbenchPath(projectPath)] = requests
  }
  if (normalizedRequestsByPath.isEmpty()) {
    return emptyConcreteCodexTabRebindReport()
  }

  val tabsService = service<AgentChatTabsService>()
  val managerByFile = LinkedHashMap<AgentChatVirtualFile, LinkedHashSet<FileEditorManagerEx>>()
  val openConcreteIdentitiesByPathAndManager = LinkedHashMap<String, LinkedHashMap<FileEditorManagerEx, LinkedHashSet<String>>>()
  val concreteFilesByPathAndTabKey = LinkedHashMap<String, LinkedHashMap<String, AgentChatVirtualFile>>()
  for (project in ProjectManager.getInstance().openProjects) {
    val manager = project.serviceIfCreated<FileEditorManager>() as? FileEditorManagerEx ?: continue
    for (openFile in manager.openFiles) {
      val chatFile = openFile as? AgentChatVirtualFile ?: continue
      val normalizedPath = normalizeAgentWorkbenchPath(chatFile.projectPath)
      managerByFile.computeIfAbsent(chatFile) { LinkedHashSet() }.add(manager)
      if (!isPendingThreadIdentity(chatFile.threadIdentity)) {
        openConcreteIdentitiesByPathAndManager
          .computeIfAbsent(normalizedPath) { LinkedHashMap() }
          .computeIfAbsent(manager) { LinkedHashSet() }
          .add(chatFile.threadIdentity)
      }
      if (
        chatFile.provider == AgentSessionProvider.CODEX &&
        !chatFile.isPendingThread &&
        chatFile.subAgentId == null
      ) {
        concreteFilesByPathAndTabKey
          .computeIfAbsent(normalizedPath) { LinkedHashMap() }
          .putIfAbsent(chatFile.tabKey, chatFile)
      }
    }
  }

  var reboundBindings = 0
  val changedFiles = LinkedHashSet<AgentChatVirtualFile>()
  val outcomesByPath = LinkedHashMap<String, MutableList<AgentChatConcreteCodexTabRebindOutcome>>()
  for ((normalizedPath, requests) in normalizedRequestsByPath) {
    val outcomes = outcomesByPath.computeIfAbsent(normalizedPath) { ArrayList(requests.size) }
    for (request in requests) {
      val concreteFile = concreteFilesByPathAndTabKey[normalizedPath]?.get(request.tabKey)
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

      val managers = managerByFile[concreteFile].orEmpty()
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
        concreteFile.provider != AgentSessionProvider.CODEX ||
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

      val targetIdentityAlreadyOpen = managers.any { manager ->
        request.target.threadIdentity in openConcreteIdentitiesByPathAndManager[normalizedPath]?.get(manager).orEmpty()
      }
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

      val previousIdentity = concreteFile.threadIdentity
      val changed = concreteFile.rebindConcreteThread(
        threadIdentity = request.target.threadIdentity,
        shellCommand = request.target.shellCommand,
        shellEnvVariables = request.target.shellEnvVariables,
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
      for (manager in managers) {
        openConcreteIdentitiesByPathAndManager[normalizedPath]?.get(manager)?.remove(previousIdentity)
        openConcreteIdentitiesByPathAndManager
          .computeIfAbsent(normalizedPath) { LinkedHashMap() }
          .computeIfAbsent(manager) { LinkedHashSet() }
          .add(request.target.threadIdentity)
      }
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
    val managers = managerByFile[changedFile].orEmpty()
    for (manager in managers) {
      manager.updateFilePresentation(changedFile)
      updatedPresentations++
    }
  }

  val requestedBindings = normalizedRequestsByPath.values.sumOf { it.size }
  val report = AgentChatConcreteCodexTabRebindReport(
    requestedBindings = requestedBindings,
    reboundBindings = reboundBindings,
    reboundFiles = changedFiles.size,
    updatedPresentations = updatedPresentations,
    outcomesByPath = outcomesByPath,
  )
  LOG.debug {
    "rebindOpenConcreteCodexTabs requestedBindings=${report.requestedBindings}, reboundBindings=${report.reboundBindings}, " +
    "reboundFiles=${report.reboundFiles}, updatedPresentations=${report.updatedPresentations}, paths=${report.outcomesByPath.size}"
  }
  return report
}

fun clearOpenConcreteCodexNewThreadRebindAnchors(
  tabsByProjectPath: Map<String, List<AgentChatConcreteCodexTabSnapshot>>,
): Int {
  if (tabsByProjectPath.isEmpty()) {
    return 0
  }

  val normalizedTabsByPath = LinkedHashMap<String, List<AgentChatConcreteCodexTabSnapshot>>()
  for ((projectPath, tabs) in tabsByProjectPath) {
    if (tabs.isEmpty()) {
      continue
    }
    normalizedTabsByPath[normalizeAgentWorkbenchPath(projectPath)] = tabs
  }
  if (normalizedTabsByPath.isEmpty()) {
    return 0
  }

  val tabsService = service<AgentChatTabsService>()
  val concreteFilesByPathAndTabKey = LinkedHashMap<String, LinkedHashMap<String, AgentChatVirtualFile>>()
  for (project in ProjectManager.getInstance().openProjects) {
    val manager = project.serviceIfCreated<FileEditorManager>() ?: continue
    for (openFile in manager.openFiles) {
      val chatFile = openFile as? AgentChatVirtualFile ?: continue
      if (
        chatFile.provider != AgentSessionProvider.CODEX ||
        chatFile.isPendingThread ||
        chatFile.subAgentId != null
      ) {
        continue
      }
      concreteFilesByPathAndTabKey
        .computeIfAbsent(normalizeAgentWorkbenchPath(chatFile.projectPath)) { LinkedHashMap() }
        .putIfAbsent(chatFile.tabKey, chatFile)
    }
  }

  var cleared = 0
  for ((normalizedPath, tabs) in normalizedTabsByPath) {
    for (tab in tabs) {
      val concreteFile = concreteFilesByPathAndTabKey[normalizedPath]?.get(tab.tabKey) ?: continue
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
    val managerByFile = LinkedHashMap<AgentChatVirtualFile, LinkedHashSet<FileEditorManagerEx>>()
    val changedFiles = LinkedHashSet<AgentChatVirtualFile>()

    for (project in ProjectManager.getInstance().openProjects) {
      val manager = project.serviceIfCreated<FileEditorManager>() as? FileEditorManagerEx ?: continue
      for (openFile in manager.openFiles) {
        val chatFile = openFile as? AgentChatVirtualFile ?: continue
        managerByFile.computeIfAbsent(chatFile) { LinkedHashSet() }.add(manager)
        val key = normalizeAgentWorkbenchPath(chatFile.projectPath) to chatFile.threadIdentity
        val targetTitle = titleByPathAndThreadIdentity[key]
        val targetActivity = activityByPathAndThreadIdentity[key]
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
    }

    updatedTabs = changedFiles.size
    updatedPresentations = 0
    for (chatFile in changedFiles) {
      val managers = managerByFile[chatFile] ?: continue
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
  for (project in ProjectManager.getInstance().openProjects) {
    val selectionService = project.serviceIfCreated<AgentChatTabSelectionService>() ?: continue
    val selection = selectionService.selectedChatTab.value ?: continue
    val identity = parseAgentThreadIdentity(selection.threadIdentity) ?: continue
    val provider = AgentSessionProvider.fromOrNull(identity.providerId) ?: continue
    return@withContext provider to identity.threadId
  }
  null
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

private fun isPendingThreadIdentity(threadIdentity: String): Boolean {
  val separator = threadIdentity.indexOf(':')
  if (separator <= 0 || separator == threadIdentity.lastIndex) {
    return false
  }
  return threadIdentity.substring(separator + 1).startsWith("new-")
}

private fun isPendingCodexThreadIdentity(threadIdentity: String): Boolean {
  val separator = threadIdentity.indexOf(':')
  if (separator <= 0 || separator == threadIdentity.lastIndex) {
    return false
  }
  val providerId = threadIdentity.substring(0, separator).lowercase()
  if (providerId != CODEX_PROVIDER_ID) {
    return false
  }
  return threadIdentity.substring(separator + 1).startsWith("new-")
}
