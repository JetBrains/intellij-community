// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.agent.workbench.common.AgentThreadActivity
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
  @JvmField val threadUpdatedAt: Long = 0L,
)

data class AgentChatPendingTabSnapshot(
  @JvmField val projectPath: String,
  @JvmField val pendingTabKey: String,
  @JvmField val pendingThreadIdentity: String,
  @JvmField val pendingCreatedAtMs: Long?,
  @JvmField val pendingFirstInputAtMs: Long?,
  @JvmField val pendingLaunchMode: String?,
)

data class AgentChatConcreteTabSnapshot(
  @JvmField val projectPath: String,
  @JvmField val tabKey: String,
  @JvmField val currentThreadIdentity: String,
  @JvmField val newThreadRebindRequestedAtMs: Long,
)

data class AgentChatPendingTabRebindRequest(
  @JvmField val pendingTabKey: String,
  @JvmField val pendingThreadIdentity: String,
  @JvmField val target: AgentChatTabRebindTarget,
)

data class AgentChatConcreteTabRebindRequest(
  @JvmField val tabKey: String,
  @JvmField val currentThreadIdentity: String,
  @JvmField val newThreadRebindRequestedAtMs: Long,
  @JvmField val target: AgentChatTabRebindTarget,
)

enum class AgentChatPendingTabRebindStatus {
  REBOUND,
  PENDING_TAB_NOT_OPEN,
  INVALID_PENDING_TAB,
  TARGET_ALREADY_OPEN,
}

data class AgentChatPendingTabRebindOutcome(
  @JvmField val projectPath: String,
  @JvmField val request: AgentChatPendingTabRebindRequest,
  @JvmField val status: AgentChatPendingTabRebindStatus,
  @JvmField val reboundFiles: Int,
)

data class AgentChatPendingTabRebindReport(
  @JvmField val requestedBindings: Int,
  @JvmField val reboundBindings: Int,
  @JvmField val reboundFiles: Int,
  @JvmField val updatedPresentations: Int,
  @JvmField val outcomesByPath: Map<String, List<AgentChatPendingTabRebindOutcome>>,
)

enum class AgentChatConcreteTabRebindStatus {
  REBOUND,
  CONCRETE_TAB_NOT_OPEN,
  INVALID_CONCRETE_TAB,
  TARGET_ALREADY_OPEN,
}

data class AgentChatConcreteTabRebindOutcome(
  @JvmField val projectPath: String,
  @JvmField val request: AgentChatConcreteTabRebindRequest,
  @JvmField val status: AgentChatConcreteTabRebindStatus,
  @JvmField val reboundFiles: Int,
)

data class AgentChatConcreteTabRebindReport(
  @JvmField val requestedBindings: Int,
  @JvmField val reboundBindings: Int,
  @JvmField val reboundFiles: Int,
  @JvmField val updatedPresentations: Int,
  @JvmField val outcomesByPath: Map<String, List<AgentChatConcreteTabRebindOutcome>>,
)

data class AgentChatPendingTabRebindTarget(
  val threadIdentity: String,
  val threadId: String,
  val shellCommand: List<String>,
  val threadTitle: String,
  val threadActivity: AgentThreadActivity,
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
    metadataStore.upsert(existing.toDescriptor())
    LOG.debug {
      "openChat existing tab update(identity=$threadIdentity, subAgentId=$subAgentId): titleUpdated=$titleUpdated, activityUpdated=$activityUpdated, currentName=${existing.name}, currentTitle=${existing.threadTitle}, currentActivity=${existing.threadActivity}"
    }
    if (titleUpdated || activityUpdated) {
      manager.updateFilePresentation(existing)
    }
    if (initialMessageUpdated && !existing.initialMessageSent) {
      flushPendingInitialMessageForOpenEditors(manager = manager, file = existing)
    }
  }
  else {
    file.updateThreadActivity(threadActivity)
    metadataStore.upsert(descriptor)
    LOG.debug {
      "openChat created new tab(identity=$threadIdentity, subAgentId=$subAgentId, fileName=${file.name}, activity=$threadActivity)"
    }
  }
  manager.openFile(
    file = file,
    options = FileEditorOpenOptions(requestFocus = true, reuseOpen = true),
  )
  LOG.debug {
    "openChat openFile completed(identity=$threadIdentity, subAgentId=$subAgentId, fileName=${file.name}, activity=$threadActivity)"
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
): Map<String, List<AgentChatPendingTabSnapshot>> {
  return collectOpenAgentChatTabsSnapshotOnUi().pendingTabsByPath(provider)
}

suspend fun collectOpenPendingCodexTabsByPath(): Map<String, List<AgentChatPendingTabSnapshot>> {
  return collectOpenPendingAgentChatTabsByPath(AgentSessionProvider.CODEX)
}

suspend fun collectOpenConcreteAgentChatTabsAwaitingNewThreadRebindByPath(
  provider: AgentSessionProvider,
): Map<String, List<AgentChatConcreteTabSnapshot>> {
  return collectOpenAgentChatTabsSnapshotOnUi().concreteTabsAwaitingNewThreadRebindByPath(provider)
}

suspend fun collectOpenConcreteCodexTabsAwaitingNewThreadRebindByPath(): Map<String, List<AgentChatConcreteTabSnapshot>> {
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

suspend fun rebindOpenAgentChatPendingTabs(
  targetsByProjectPath: Map<String, List<AgentChatPendingTabRebindTarget>>,
): Int {
  if (targetsByProjectPath.isEmpty()) {
    return 0
  }

  val metadataStore = AgentChatTabMetadataStores.getInstance()
  val updatedDescriptors = ArrayList<AgentChatFileDescriptor>()
  var reboundTabs: Int
  var updatedPresentations: Int
  withContext(Dispatchers.UI) {
    val managerByFile = LinkedHashMap<AgentChatVirtualFile, LinkedHashSet<FileEditorManagerEx>>()
    val openConcreteIdentitiesByPath = LinkedHashMap<String, LinkedHashSet<String>>()
    val pendingFilesByPath = LinkedHashMap<String, MutableList<AgentChatVirtualFile>>()

    for (project in ProjectManager.getInstance().openProjects) {
      val manager = runCatching { FileEditorManagerEx.getInstanceEx(project) }.getOrNull() ?: continue
      for (openFile in manager.openFiles) {
        val chatFile = openFile as? AgentChatVirtualFile ?: continue
        managerByFile.getOrPut(chatFile) { LinkedHashSet() }.add(manager)
        val normalizedPath = normalizeAgentChatProjectPath(chatFile.projectPath)
        if (isPendingThreadIdentity(chatFile.threadIdentity)) {
          pendingFilesByPath.getOrPut(normalizedPath) { ArrayList() }.add(chatFile)
        }
        else {
          openConcreteIdentitiesByPath.getOrPut(normalizedPath) { LinkedHashSet() }.add(chatFile.threadIdentity)
        }
      }
    }

    val changedFiles = LinkedHashSet<AgentChatVirtualFile>()
    for ((projectPath, pendingFiles) in pendingFilesByPath) {
      val targets = targetsByProjectPath[projectPath].orEmpty()
      if (targets.isEmpty()) {
        continue
      }

      val knownIdentities = openConcreteIdentitiesByPath.getOrPut(projectPath) { LinkedHashSet() }
      var targetIndex = 0
      for (pendingFile in pendingFiles) {
        while (targetIndex < targets.size && targets[targetIndex].threadIdentity in knownIdentities) {
          targetIndex++
        }
        if (targetIndex >= targets.size) {
          break
        }

        val target = targets[targetIndex++]
        if (pendingFile.rebindPendingThread(
            threadIdentity = target.threadIdentity,
            shellCommand = target.shellCommand,
            threadId = target.threadId,
            threadTitle = target.threadTitle,
            threadActivity = target.threadActivity,
          )) {
          knownIdentities.add(target.threadIdentity)
          updatedDescriptors.add(pendingFile.toDescriptor())
          changedFiles.add(pendingFile)
        }
      }
    }

    reboundTabs = changedFiles.size
    updatedPresentations = 0
    for (chatFile in changedFiles) {
      val managers = managerByFile[chatFile] ?: continue
      for (manager in managers) {
        manager.updateFilePresentation(chatFile)
        updatedPresentations++
      }
    }
  }

  if (updatedDescriptors.isNotEmpty()) {
    withContext(Dispatchers.IO) {
      for (descriptor in updatedDescriptors) {
        metadataStore.upsert(descriptor)
      }
    }
  }

  LOG.debug {
    "rebindOpenAgentChatPendingTabs reboundTabs=$reboundTabs, updatedPresentations=$updatedPresentations, requestedPaths=${targetsByProjectPath.size}"
  }
  return reboundTabs
}

suspend fun updateOpenAgentChatTabPresentation(
  titleByPathAndThreadIdentity: Map<Pair<String, String>, String>,
  activityByPathAndThreadIdentity: Map<Pair<String, String>, AgentThreadActivity>,
): Int {
  if (titleByPathAndThreadIdentity.isEmpty() && activityByPathAndThreadIdentity.isEmpty()) {
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
  tabsByProjectPath: Map<String, List<AgentChatConcreteTabSnapshot>>,
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

    for (project in ProjectManager.getInstance().openProjects) {
      val manager = runCatching { FileEditorManagerEx.getInstanceEx(project) }.getOrNull() ?: continue
      for (openFile in manager.openFiles) {
        val chatFile = openFile as? AgentChatVirtualFile ?: continue
        managerByFile.getOrPut(chatFile) { LinkedHashSet() }.add(manager)
        val key = normalizeAgentChatProjectPath(chatFile.projectPath) to chatFile.threadIdentity
        val targetTitle = titleByPathAndThreadIdentity[key]
        val targetActivity = activityByPathAndThreadIdentity[key]
        if (targetTitle == null && targetActivity == null) {
          continue
        }

        var presentationUpdated = false
        if (targetTitle != null && chatFile.updateThreadTitle(targetTitle)) {
          updatedDescriptors.add(chatFile.toDescriptor())
          presentationUpdated = true
        }
        if (targetActivity != null && chatFile.updateThreadActivity(targetActivity)) {
          presentationUpdated = true
        }

        if (presentationUpdated) {
          changedFiles.add(chatFile)
        }
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
    "updateOpenAgentChatTabPresentation updatedTabs=$updatedTabs, updatedPresentations=$updatedPresentations, requestedTitles=${titleByPathAndThreadIdentity.size}, requestedActivities=${activityByPathAndThreadIdentity.size}"
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

private fun isPendingThreadIdentity(threadIdentity: String): Boolean {
  val separator = threadIdentity.indexOf(':')
  if (separator <= 0 || separator == threadIdentity.lastIndex) {
    return false
  }
  return threadIdentity.substring(separator + 1).startsWith("new-")
}
