// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class AgentChatOpenTabsRefreshSnapshot(
  @JvmField val openProjectPaths: Set<String>,
  val selectedChatThreadIdentity: Pair<AgentSessionProvider, String>?,
  private val pendingTabsByProvider: Map<AgentSessionProvider, Map<String, List<AgentChatPendingTabSnapshot>>>,
  private val concreteTabsAwaitingNewThreadRebindByProvider: Map<AgentSessionProvider, Map<String, List<AgentChatConcreteTabSnapshot>>>,
  @JvmField val concreteThreadIdentitiesByPath: Map<String, Set<String>>,
) {
  fun pendingTabsByPath(provider: AgentSessionProvider): Map<String, List<AgentChatPendingTabSnapshot>> {
    return pendingTabsByProvider[provider].orEmpty()
  }

  fun concreteTabsAwaitingNewThreadRebindByPath(provider: AgentSessionProvider): Map<String, List<AgentChatConcreteTabSnapshot>> {
    return concreteTabsAwaitingNewThreadRebindByProvider[provider].orEmpty()
  }
}

internal suspend fun collectOpenAgentChatTabsSnapshotOnUi(): AgentChatOpenTabsSnapshot = withContext(Dispatchers.UI) {
  collectOpenAgentChatTabsSnapshot()
}

suspend fun collectOpenAgentChatRefreshSnapshot(): AgentChatOpenTabsRefreshSnapshot = withContext(Dispatchers.UI) {
  collectOpenAgentChatTabsSnapshot().toRefreshSnapshot()
}

internal fun collectOpenAgentChatTabsSnapshot(
  projects: Array<Project> = ProjectManager.getInstance().openProjects,
): AgentChatOpenTabsSnapshot {
  val entries = ArrayList<AgentChatOpenFileEntry>()
  val filesByTabKey = LinkedHashMap<String, AgentChatVirtualFile>()
  val managerByFile = LinkedHashMap<AgentChatVirtualFile, LinkedHashSet<FileEditorManagerEx>>()
  val concreteThreadIdentitiesByPath = LinkedHashMap<String, LinkedHashSet<String>>()
  val concreteThreadIdentitiesByPathAndManager = LinkedHashMap<String, LinkedHashMap<FileEditorManagerEx, LinkedHashSet<String>>>()
  val pendingFilesByProviderAndPathAndTabKey =
    LinkedHashMap<AgentSessionProvider, LinkedHashMap<String, LinkedHashMap<String, AgentChatVirtualFile>>>()
  val concreteFilesByProviderAndPathAndTabKey =
    LinkedHashMap<AgentSessionProvider, LinkedHashMap<String, LinkedHashMap<String, AgentChatVirtualFile>>>()
  val openProjectPaths = LinkedHashSet<String>()
  val pendingProjectPaths = LinkedHashSet<String>()
  var selectedChatThreadIdentity: Pair<AgentSessionProvider, String>? = null

  for (project in projects) {
    if (project.isDisposed) {
      continue
    }

    if (selectedChatThreadIdentity == null) {
      val selection = project.serviceIfCreated<AgentChatTabSelectionService>()?.selectedChatTab?.value
      val identity = selection?.let { splitAgentThreadIdentity(it.threadIdentity) }
      val provider = identity?.let { AgentSessionProvider.fromOrNull(it.first.lowercase(Locale.ROOT)) }
      if (provider != null) {
        selectedChatThreadIdentity = provider to identity.second
      }
    }

    val manager = project.serviceIfCreated<FileEditorManager>() ?: continue
    val exManager = manager as? FileEditorManagerEx
    for (openFile in manager.openFiles) {
      val chatFile = openFile as? AgentChatVirtualFile ?: continue
      val normalizedProjectPath = normalizeAgentWorkbenchPath(chatFile.projectPath)
      val isPendingThread = isPendingThreadIdentity(chatFile.threadIdentity)
      val pendingProvider = pendingProviderForThreadIdentity(chatFile.threadIdentity)
      entries.add(
        AgentChatOpenFileEntry(
          manager = manager,
          normalizedProjectPath = normalizedProjectPath,
          file = chatFile,
        )
      )
      filesByTabKey.putIfAbsent(chatFile.tabKey, chatFile)
      openProjectPaths.add(normalizedProjectPath)
      if (isPendingThread) {
        pendingProjectPaths.add(normalizedProjectPath)
      }

      if (pendingProvider != null) {
        pendingFilesByProviderAndPathAndTabKey
          .computeIfAbsent(pendingProvider) { LinkedHashMap() }
          .computeIfAbsent(normalizedProjectPath) { LinkedHashMap() }
          .putIfAbsent(chatFile.tabKey, chatFile)
      }

      if (!isPendingThread) {
        concreteThreadIdentitiesByPath.computeIfAbsent(normalizedProjectPath) { LinkedHashSet() }.add(chatFile.threadIdentity)
        if (exManager != null) {
          concreteThreadIdentitiesByPathAndManager
            .computeIfAbsent(normalizedProjectPath) { LinkedHashMap() }
            .computeIfAbsent(exManager) { LinkedHashSet() }
            .add(chatFile.threadIdentity)
        }
      }

      if (exManager != null) {
        managerByFile.computeIfAbsent(chatFile) { LinkedHashSet() }.add(exManager)
      }

      if (chatFile.provider != null && !isPendingThread && chatFile.subAgentId == null) {
        concreteFilesByProviderAndPathAndTabKey
          .computeIfAbsent(chatFile.provider!!) { LinkedHashMap() }
          .computeIfAbsent(normalizedProjectPath) { LinkedHashMap() }
          .putIfAbsent(chatFile.tabKey, chatFile)
      }
    }
  }

  return AgentChatOpenTabsSnapshot(
    entries = entries,
    filesByTabKey = filesByTabKey,
    managerByFile = managerByFile,
    concreteThreadIdentitiesByPath = concreteThreadIdentitiesByPath,
    concreteThreadIdentitiesByPathAndManager = concreteThreadIdentitiesByPathAndManager,
    pendingFilesByProviderAndPathAndTabKey = pendingFilesByProviderAndPathAndTabKey,
    concreteFilesByProviderAndPathAndTabKey = concreteFilesByProviderAndPathAndTabKey,
    openProjectPaths = openProjectPaths,
    pendingProjectPaths = pendingProjectPaths,
    selectedChatThreadIdentity = selectedChatThreadIdentity,
  )
}

internal data class AgentChatOpenFileEntry(
  val manager: FileEditorManager,
  val normalizedProjectPath: String,
  val file: AgentChatVirtualFile,
)

internal class AgentChatOpenTabsSnapshot(
  private val entries: List<AgentChatOpenFileEntry>,
  private val filesByTabKey: LinkedHashMap<String, AgentChatVirtualFile>,
  private val managerByFile: LinkedHashMap<AgentChatVirtualFile, LinkedHashSet<FileEditorManagerEx>>,
  private val concreteThreadIdentitiesByPath: LinkedHashMap<String, LinkedHashSet<String>>,
  private val concreteThreadIdentitiesByPathAndManager: LinkedHashMap<String, LinkedHashMap<FileEditorManagerEx, LinkedHashSet<String>>>,
  private val pendingFilesByProviderAndPathAndTabKey:
    LinkedHashMap<AgentSessionProvider, LinkedHashMap<String, LinkedHashMap<String, AgentChatVirtualFile>>>,
  private val concreteFilesByProviderAndPathAndTabKey:
    LinkedHashMap<AgentSessionProvider, LinkedHashMap<String, LinkedHashMap<String, AgentChatVirtualFile>>>,
  private val openProjectPaths: LinkedHashSet<String>,
  private val pendingProjectPaths: LinkedHashSet<String>,
  val selectedChatThreadIdentity: Pair<AgentSessionProvider, String>?,
) {
  fun findFileByTabKey(tabKey: String): AgentChatVirtualFile? {
    return filesByTabKey[tabKey]
  }

  fun files(): Collection<AgentChatVirtualFile> {
    return filesByTabKey.values
  }

  fun projectPaths(includePendingOnly: Boolean): Set<String> {
    return LinkedHashSet(if (includePendingOnly) pendingProjectPaths else openProjectPaths)
  }

  fun pendingTabsByPath(provider: AgentSessionProvider): Map<String, List<AgentChatPendingTabSnapshot>> {
    val filesByPath = pendingFilesByProviderAndPathAndTabKey[provider].orEmpty()
    val result = LinkedHashMap<String, List<AgentChatPendingTabSnapshot>>(filesByPath.size)
    for ((normalizedPath, filesByTabKey) in filesByPath) {
      val tabs = ArrayList<AgentChatPendingTabSnapshot>(filesByTabKey.size)
      for (chatFile in filesByTabKey.values) {
        tabs.add(
          AgentChatPendingTabSnapshot(
            projectPath = normalizedPath,
            pendingTabKey = chatFile.tabKey,
            pendingThreadIdentity = chatFile.threadIdentity,
            pendingCreatedAtMs = chatFile.pendingCreatedAtMs,
            pendingFirstInputAtMs = chatFile.pendingFirstInputAtMs,
            pendingLaunchMode = chatFile.pendingLaunchMode,
          )
        )
      }
      if (tabs.isNotEmpty()) {
        result[normalizedPath] = tabs
      }
    }
    return result
  }

  fun concreteTabsAwaitingNewThreadRebindByPath(provider: AgentSessionProvider): Map<String, List<AgentChatConcreteTabSnapshot>> {
    val filesByPath = concreteFilesByProviderAndPathAndTabKey[provider].orEmpty()
    val result = LinkedHashMap<String, List<AgentChatConcreteTabSnapshot>>(filesByPath.size)
    for ((normalizedPath, filesByTabKey) in filesByPath) {
      val tabs = ArrayList<AgentChatConcreteTabSnapshot>(filesByTabKey.size)
      for (chatFile in filesByTabKey.values) {
        val requestedAtMs = chatFile.newThreadRebindRequestedAtMs ?: continue
        tabs.add(
          AgentChatConcreteTabSnapshot(
            projectPath = normalizedPath,
            tabKey = chatFile.tabKey,
            currentThreadIdentity = chatFile.threadIdentity,
            newThreadRebindRequestedAtMs = requestedAtMs,
          )
        )
      }
      if (tabs.isNotEmpty()) {
        result[normalizedPath] = tabs
      }
    }
    return result
  }

  fun concreteThreadIdentitiesByPath(): Map<String, Set<String>> {
    val result = LinkedHashMap<String, Set<String>>(concreteThreadIdentitiesByPath.size)
    for ((normalizedPath, identities) in concreteThreadIdentitiesByPath) {
      result[normalizedPath] = LinkedHashSet(identities)
    }
    return result
  }

  fun findPendingFile(provider: AgentSessionProvider, normalizedPath: String, tabKey: String): AgentChatVirtualFile? {
    return pendingFilesByProviderAndPathAndTabKey[provider]?.get(normalizedPath)?.get(tabKey)
  }

  fun findConcreteFile(provider: AgentSessionProvider, normalizedPath: String, tabKey: String): AgentChatVirtualFile? {
    return concreteFilesByProviderAndPathAndTabKey[provider]?.get(normalizedPath)?.get(tabKey)
  }

  fun managersFor(file: AgentChatVirtualFile): Set<FileEditorManagerEx> {
    return managerByFile[file].orEmpty()
  }

  fun isConcreteThreadIdentityOpenInAnyManager(
    normalizedPath: String,
    managers: Set<FileEditorManagerEx>,
    threadIdentity: String,
  ): Boolean {
    return managers.any { manager ->
      threadIdentity in concreteThreadIdentitiesByPathAndManager[normalizedPath]?.get(manager).orEmpty()
    }
  }

  fun recordConcreteThreadIdentityOpen(
    normalizedPath: String,
    managers: Set<FileEditorManagerEx>,
    threadIdentity: String,
  ) {
    for (manager in managers) {
      concreteThreadIdentitiesByPathAndManager
        .computeIfAbsent(normalizedPath) { LinkedHashMap() }
        .computeIfAbsent(manager) { LinkedHashSet() }
        .add(threadIdentity)
    }
  }

  fun replaceConcreteThreadIdentity(
    normalizedPath: String,
    managers: Set<FileEditorManagerEx>,
    previousIdentity: String,
    threadIdentity: String,
  ) {
    for (manager in managers) {
      concreteThreadIdentitiesByPathAndManager[normalizedPath]?.get(manager)?.remove(previousIdentity)
      concreteThreadIdentitiesByPathAndManager
        .computeIfAbsent(normalizedPath) { LinkedHashMap() }
        .computeIfAbsent(manager) { LinkedHashSet() }
        .add(threadIdentity)
    }
  }

  fun closeMatchingOpenTabs(projectPath: String, threadIdentity: String, subAgentId: String?): Int {
    var closedTabs = 0
    for (entry in entries) {
      val chatFile = entry.file
      if (
        entry.normalizedProjectPath == projectPath &&
        chatFile.threadIdentity == threadIdentity &&
        (subAgentId == null || chatFile.subAgentId == subAgentId)
      ) {
        entry.manager.closeFile(chatFile)
        closedTabs++
      }
    }
    return closedTabs
  }

  fun toRefreshSnapshot(): AgentChatOpenTabsRefreshSnapshot {
    val pendingTabsByProvider = LinkedHashMap<AgentSessionProvider, Map<String, List<AgentChatPendingTabSnapshot>>>(
      pendingFilesByProviderAndPathAndTabKey.size
    )
    for (provider in pendingFilesByProviderAndPathAndTabKey.keys) {
      pendingTabsByProvider[provider] = pendingTabsByPath(provider)
    }

    val concreteTabsByProvider = LinkedHashMap<AgentSessionProvider, Map<String, List<AgentChatConcreteTabSnapshot>>>(
      concreteFilesByProviderAndPathAndTabKey.size
    )
    for (provider in concreteFilesByProviderAndPathAndTabKey.keys) {
      concreteTabsByProvider[provider] = concreteTabsAwaitingNewThreadRebindByPath(provider)
    }

    return AgentChatOpenTabsRefreshSnapshot(
      openProjectPaths = LinkedHashSet(openProjectPaths),
      selectedChatThreadIdentity = selectedChatThreadIdentity,
      pendingTabsByProvider = pendingTabsByProvider,
      concreteTabsAwaitingNewThreadRebindByProvider = concreteTabsByProvider,
      concreteThreadIdentitiesByPath = concreteThreadIdentitiesByPath(),
    )
  }
}

internal fun isPendingThreadIdentity(threadIdentity: String): Boolean {
  return splitAgentThreadIdentity(threadIdentity)?.second?.startsWith("new-") == true
}

internal fun isPendingThreadIdentityForProvider(threadIdentity: String, provider: AgentSessionProvider): Boolean {
  val identity = splitAgentThreadIdentity(threadIdentity) ?: return false
  if (!provider.value.equals(identity.first, ignoreCase = true)) {
    return false
  }
  return identity.second.startsWith("new-")
}

internal fun pendingProviderForThreadIdentity(threadIdentity: String): AgentSessionProvider? {
  val identity = splitAgentThreadIdentity(threadIdentity) ?: return null
  if (!identity.second.startsWith("new-")) {
    return null
  }
  return AgentSessionProvider.fromOrNull(identity.first.lowercase(Locale.ROOT))
}

internal fun splitAgentThreadIdentity(threadIdentity: String): Pair<String, String>? {
  val separator = threadIdentity.indexOf(':')
  if (separator <= 0 || separator == threadIdentity.lastIndex) {
    return null
  }

  val providerId = threadIdentity.substring(0, separator)
  val threadId = threadIdentity.substring(separator + 1)
  if (threadId.isBlank()) {
    return null
  }
  return providerId to threadId
}
