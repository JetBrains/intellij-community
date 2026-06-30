// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextTargetCandidate
import com.intellij.platform.ai.agent.sessions.core.isAgentSessionPendingThreadId
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.Locale

data class AgentThreadViewOpenTabsRefreshSnapshot(
  @JvmField val openProjectPaths: Set<String>,
  @JvmField val projectPathAliasesByPath: Map<String, Set<String>> = emptyMap(),
  val selectedThreadViewThreadIdentity: Pair<AgentSessionProvider, String>?,
  private val pendingTabsByProvider: Map<AgentSessionProvider, Map<String, List<AgentThreadViewPendingTabSnapshot>>>,
  private val concreteTabsAwaitingNewThreadRebindByProvider: Map<AgentSessionProvider, Map<String, List<AgentThreadViewConcreteTabSnapshot>>>,
  @JvmField val concreteThreadIdentitiesByPath: Map<String, Set<String>>,
) {
  fun pendingTabsByPath(provider: AgentSessionProvider): Map<String, List<AgentThreadViewPendingTabSnapshot>> {
    return pendingTabsByProvider[provider].orEmpty()
  }

  fun concreteTabsAwaitingNewThreadRebindByPath(provider: AgentSessionProvider): Map<String, List<AgentThreadViewConcreteTabSnapshot>> {
    return concreteTabsAwaitingNewThreadRebindByProvider[provider].orEmpty()
  }
}

internal suspend fun collectOpenAgentThreadViewTabsSnapshotOnUi(): AgentThreadViewOpenTabsSnapshot = withContext(Dispatchers.UI) {
  collectOpenAgentThreadViewTabsSnapshot()
}

suspend fun collectOpenAgentThreadViewRefreshSnapshot(): AgentThreadViewOpenTabsRefreshSnapshot = withContext(Dispatchers.UI) {
  collectOpenAgentThreadViewTabsSnapshot().toRefreshSnapshot()
}

suspend fun collectOpenAgentThreadViewAddContextTargetCandidates(projectPath: String): List<AgentPromptAddContextTargetCandidate> =
  withContext(Dispatchers.UI) {
    collectOpenAgentThreadViewTabsSnapshot().addContextTargetCandidates(normalizeAgentWorkbenchPath(projectPath))
  }

@ApiStatus.Internal
suspend fun setAgentThreadViewEditorTabPinned(project: Project, file: VirtualFile, pinned: Boolean): Unit = withContext(Dispatchers.UI) {
  FileEditorManager.getInstance(project).setPinnedEditorTab(file, pinned)
}

@ApiStatus.Internal
suspend fun setOpenTopLevelAgentThreadViewThreadTabsPinned(
  provider: AgentSessionProvider,
  projectPath: String,
  threadId: String,
  pinned: Boolean,
): Int = withContext(Dispatchers.UI) {
  collectOpenAgentThreadViewTabsSnapshot().setTopLevelConcreteThreadTabsPinned(
    provider = provider,
    normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath),
    threadId = threadId,
    pinned = pinned,
  )
}

internal fun collectOpenAgentThreadViewTabsSnapshot(
  projects: Array<Project> = ProjectManager.getInstance().openProjects,
): AgentThreadViewOpenTabsSnapshot {
  val entries = ArrayList<AgentThreadViewOpenFileEntry>()
  val filesByTabKey = LinkedHashMap<String, AgentThreadViewVirtualFile>()
  val managerByFile = LinkedHashMap<AgentThreadViewVirtualFile, LinkedHashSet<FileEditorManagerEx>>()
  val concreteThreadIdentitiesByPath = LinkedHashMap<String, LinkedHashSet<String>>()
  val pinnedTopLevelConcreteThreadIdentitiesByProviderAndPath =
    LinkedHashMap<AgentSessionProvider, LinkedHashMap<String, LinkedHashSet<String>>>()
  val pinnedTabKeys = LinkedHashSet<String>()
  val concreteThreadIdentitiesByPathAndManager = ConcreteThreadIdentitiesByManager()
  val topLevelConcreteThreadIdentitiesByPathAndManager = ConcreteThreadIdentitiesByManager()
  val projectPathAliasesByPath = LinkedHashMap<String, LinkedHashSet<String>>()
  val pendingFilesByProviderAndPathAndTabKey =
    LinkedHashMap<AgentSessionProvider, LinkedHashMap<String, LinkedHashMap<String, AgentThreadViewVirtualFile>>>()
  val concreteFilesByProviderAndPathAndTabKey =
    LinkedHashMap<AgentSessionProvider, LinkedHashMap<String, LinkedHashMap<String, AgentThreadViewVirtualFile>>>()
  val openProjectPaths = LinkedHashSet<String>()
  val pendingProjectPaths = LinkedHashSet<String>()
  var selectedThreadViewThreadIdentity: Pair<AgentSessionProvider, String>? = null
  var selectedTopLevelConcreteThreadViewTab: AgentThreadViewSelectedTopLevelConcreteTab? = null

  for (project in projects) {
    if (project.isDisposed) {
      continue
    }

    if (selectedTopLevelConcreteThreadViewTab == null) {
      val selection = project.serviceIfCreated<AgentThreadViewTabSelectionService>()?.selectedThreadViewTab?.value
      val identity = selection?.let { splitAgentThreadIdentity(it.threadIdentity) }
      val provider = identity?.let { AgentSessionProvider.fromOrNull(it.first.lowercase(Locale.ROOT)) }
      if (selection != null && provider != null && selection.subAgentId == null &&
          selection.threadId.isNotBlank() && !isPendingThreadIdentityForProvider(selection.threadIdentity, provider)
      ) {
        selectedThreadViewThreadIdentity = provider to selection.threadId
        selectedTopLevelConcreteThreadViewTab = AgentThreadViewSelectedTopLevelConcreteTab(
          normalizedProjectPath = normalizeAgentWorkbenchPath(selection.projectPath),
          provider = provider,
          threadId = selection.threadId,
        )
      }
    }

    val manager = project.serviceIfCreated<FileEditorManager>() ?: continue
    val exManager = manager as? FileEditorManagerEx
    for (openFile in manager.openFiles) {
      val threadViewFile = openFile as? AgentThreadViewVirtualFile ?: continue
      if (threadViewFile.projectPath.isBlank() || threadViewFile.threadIdentity.isBlank()) {
        continue
      }
      val normalizedProjectPath = normalizeAgentWorkbenchPath(threadViewFile.projectPath)
      recordProjectPathAlias(
        projectPathAliasesByPath = projectPathAliasesByPath,
        normalizedProjectPath = normalizedProjectPath,
        projectDirectory = threadViewFile.projectDirectory,
      )
      val hasPendingThreadIdentity = threadViewFile.isPendingThread
      val participatesInPendingThreadLifecycle = threadViewFile.participatesInPendingThreadLifecycle()
      val pendingProvider =
        if (participatesInPendingThreadLifecycle) pendingProviderForThreadIdentity(threadViewFile.threadIdentity) else null
      entries.add(
        AgentThreadViewOpenFileEntry(
          manager = manager,
          normalizedProjectPath = normalizedProjectPath,
          file = threadViewFile,
        )
      )
      filesByTabKey.putIfAbsent(threadViewFile.tabKey, threadViewFile)
      val pinnedEditorTab = manager.hasPinnedEditorTab(threadViewFile)
      if (pinnedEditorTab) {
        pinnedTabKeys.add(threadViewFile.tabKey)
      }
      openProjectPaths.add(normalizedProjectPath)
      if (participatesInPendingThreadLifecycle) {
        pendingProjectPaths.add(normalizedProjectPath)
      }

      if (pendingProvider != null) {
        pendingFilesByProviderAndPathAndTabKey
          .computeIfAbsent(pendingProvider) { LinkedHashMap() }
          .computeIfAbsent(normalizedProjectPath) { LinkedHashMap() }
          .putIfAbsent(threadViewFile.tabKey, threadViewFile)
      }

      if (!hasPendingThreadIdentity) {
        concreteThreadIdentitiesByPath.computeIfAbsent(normalizedProjectPath) { LinkedHashSet() }.add(threadViewFile.threadIdentity)
        if (exManager != null) {
          concreteThreadIdentitiesByPathAndManager
            .computeIfAbsent(normalizedProjectPath) { LinkedHashMap() }
            .computeIfAbsent(exManager) { LinkedHashSet() }
            .add(threadViewFile.threadIdentity)
        }
      }

      if (exManager != null) {
        managerByFile.computeIfAbsent(threadViewFile) { LinkedHashSet() }.add(exManager)
      }

      val provider = threadViewFile.provider
      if (provider != null && !hasPendingThreadIdentity && threadViewFile.subAgentId == null) {
        if (pinnedEditorTab) {
          pinnedTopLevelConcreteThreadIdentitiesByProviderAndPath
            .computeIfAbsent(provider) { LinkedHashMap() }
            .computeIfAbsent(normalizedProjectPath) { LinkedHashSet() }
            .add(threadViewFile.threadId)
        }
        concreteFilesByProviderAndPathAndTabKey
          .computeIfAbsent(provider) { LinkedHashMap() }
          .computeIfAbsent(normalizedProjectPath) { LinkedHashMap() }
          .putIfAbsent(threadViewFile.tabKey, threadViewFile)
        if (exManager != null) {
          topLevelConcreteThreadIdentitiesByPathAndManager
            .computeIfAbsent(normalizedProjectPath) { LinkedHashMap() }
            .computeIfAbsent(exManager) { LinkedHashSet() }
            .add(threadViewFile.threadIdentity)
        }
      }
    }
  }

  return AgentThreadViewOpenTabsSnapshot(
    entries = entries,
    filesByTabKey = filesByTabKey,
    managerByFile = managerByFile,
    concreteThreadIdentitiesByPath = concreteThreadIdentitiesByPath,
    concreteThreadIdentitiesByPathAndManager = concreteThreadIdentitiesByPathAndManager,
    topLevelConcreteThreadIdentitiesByPathAndManager = topLevelConcreteThreadIdentitiesByPathAndManager,
    pinnedTopLevelConcreteThreadIdentitiesByProviderAndPath = pinnedTopLevelConcreteThreadIdentitiesByProviderAndPath,
    pinnedTabKeys = pinnedTabKeys,
    pendingFilesByProviderAndPathAndTabKey = pendingFilesByProviderAndPathAndTabKey,
    concreteFilesByProviderAndPathAndTabKey = concreteFilesByProviderAndPathAndTabKey,
    projectPathAliasesByPath = projectPathAliasesByPath,
    openProjectPaths = openProjectPaths,
    pendingProjectPaths = pendingProjectPaths,
    selectedThreadViewThreadIdentity = selectedThreadViewThreadIdentity,
    selectedTopLevelConcreteThreadViewTab = selectedTopLevelConcreteThreadViewTab,
  )
}

internal data class AgentThreadViewOpenFileEntry(
  val manager: FileEditorManager,
  val normalizedProjectPath: String,
  val file: AgentThreadViewVirtualFile,
)

internal data class AgentThreadViewSelectedTopLevelConcreteTab(
  @JvmField val normalizedProjectPath: String,
  val provider: AgentSessionProvider,
  @JvmField val threadId: String,
)

private typealias ConcreteThreadIdentitiesByManager = LinkedHashMap<String, LinkedHashMap<FileEditorManagerEx, LinkedHashSet<String>>>

internal class AgentThreadViewOpenTabsSnapshot(
  private val entries: List<AgentThreadViewOpenFileEntry>,
  private val filesByTabKey: LinkedHashMap<String, AgentThreadViewVirtualFile>,
  private val managerByFile: LinkedHashMap<AgentThreadViewVirtualFile, LinkedHashSet<FileEditorManagerEx>>,
  private val concreteThreadIdentitiesByPath: LinkedHashMap<String, LinkedHashSet<String>>,
  private val concreteThreadIdentitiesByPathAndManager: ConcreteThreadIdentitiesByManager,
  private val topLevelConcreteThreadIdentitiesByPathAndManager: ConcreteThreadIdentitiesByManager,
  private val pinnedTopLevelConcreteThreadIdentitiesByProviderAndPath:
  LinkedHashMap<AgentSessionProvider, LinkedHashMap<String, LinkedHashSet<String>>>,
  private val pinnedTabKeys: LinkedHashSet<String>,
  private val pendingFilesByProviderAndPathAndTabKey:
  LinkedHashMap<AgentSessionProvider, LinkedHashMap<String, LinkedHashMap<String, AgentThreadViewVirtualFile>>>,
  private val concreteFilesByProviderAndPathAndTabKey:
  LinkedHashMap<AgentSessionProvider, LinkedHashMap<String, LinkedHashMap<String, AgentThreadViewVirtualFile>>>,
  private val projectPathAliasesByPath: LinkedHashMap<String, LinkedHashSet<String>>,
  private val openProjectPaths: LinkedHashSet<String>,
  private val pendingProjectPaths: LinkedHashSet<String>,
  val selectedThreadViewThreadIdentity: Pair<AgentSessionProvider, String>?,
  private val selectedTopLevelConcreteThreadViewTab: AgentThreadViewSelectedTopLevelConcreteTab?,
) {
  private val closedTopLevelConcreteEntries = LinkedHashSet<AgentThreadViewOpenFileEntry>()

  fun findFileByTabKey(tabKey: String): AgentThreadViewVirtualFile? {
    return filesByTabKey[tabKey]
  }

  fun files(): Collection<AgentThreadViewVirtualFile> {
    return filesByTabKey.values
  }

  fun setTopLevelConcreteThreadTabsPinned(
    provider: AgentSessionProvider,
    normalizedProjectPath: String,
    threadId: String,
    pinned: Boolean,
  ): Int {
    val processedEntries = LinkedHashSet<Pair<FileEditorManager, AgentThreadViewVirtualFile>>()
    var updatedCount = 0
    for ((manager, entryProjectPath, threadViewFile) in entries) {
      if (entryProjectPath != normalizedProjectPath ||
          threadViewFile.provider != provider ||
          threadViewFile.sessionId != threadId ||
          threadViewFile.isPendingThread ||
          threadViewFile.subAgentId != null) {
        continue
      }
      if (!processedEntries.add(manager to threadViewFile) || !manager.isFileOpen(threadViewFile)) {
        continue
      }
      if (manager.hasPinnedEditorTab(threadViewFile) != pinned) {
        manager.setPinnedEditorTab(threadViewFile, pinned)
        updatedCount++
      }
    }
    return updatedCount
  }

  fun projectPaths(includePendingOnly: Boolean): Set<String> {
    return LinkedHashSet(if (includePendingOnly) pendingProjectPaths else openProjectPaths)
  }

  fun pendingTabsByPath(provider: AgentSessionProvider): Map<String, List<AgentThreadViewPendingTabSnapshot>> {
    val filesByPath = pendingFilesByProviderAndPathAndTabKey[provider].orEmpty()
    val result = LinkedHashMap<String, List<AgentThreadViewPendingTabSnapshot>>(filesByPath.size)
    for ((normalizedPath, filesByTabKey) in filesByPath) {
      val tabs = ArrayList<AgentThreadViewPendingTabSnapshot>(filesByTabKey.size)
      for (threadViewFile in filesByTabKey.values) {
        tabs.add(
          AgentThreadViewPendingTabSnapshot(
            projectPath = normalizedPath,
            pendingTabKey = threadViewFile.tabKey,
            pendingThreadIdentity = threadViewFile.threadIdentity,
            pendingCreatedAtMs = threadViewFile.pendingCreatedAtMs,
            pendingFirstInputAtMs = threadViewFile.pendingFirstInputAtMs,
            pendingLaunchMode = threadViewFile.pendingLaunchMode,
            pinnedEditorTab = threadViewFile.tabKey in pinnedTabKeys,
          )
        )
      }
      if (tabs.isNotEmpty()) {
        result[normalizedPath] = tabs
      }
    }
    return result
  }

  fun concreteTabsAwaitingNewThreadRebindByPath(provider: AgentSessionProvider): Map<String, List<AgentThreadViewConcreteTabSnapshot>> {
    val filesByPath = concreteFilesByProviderAndPathAndTabKey[provider].orEmpty()
    val result = LinkedHashMap<String, List<AgentThreadViewConcreteTabSnapshot>>(filesByPath.size)
    for ((normalizedPath, filesByTabKey) in filesByPath) {
      val tabs = ArrayList<AgentThreadViewConcreteTabSnapshot>(filesByTabKey.size)
      for (threadViewFile in filesByTabKey.values) {
        val requestedAtMs = threadViewFile.newThreadRebindRequestedAtMs ?: continue
        tabs.add(
          AgentThreadViewConcreteTabSnapshot(
            projectPath = normalizedPath,
            tabKey = threadViewFile.tabKey,
            currentThreadIdentity = threadViewFile.threadIdentity,
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

  fun pinnedTopLevelConcreteThreadIdentitiesByPath(provider: AgentSessionProvider): Map<String, Set<String>> {
    val pinnedByPath = pinnedTopLevelConcreteThreadIdentitiesByProviderAndPath[provider].orEmpty()
    val result = LinkedHashMap<String, Set<String>>(pinnedByPath.size)
    for ((normalizedPath, threadIds) in pinnedByPath) {
      result[normalizedPath] = LinkedHashSet(threadIds)
    }
    return result
  }

  fun findPendingFile(provider: AgentSessionProvider, normalizedPath: String, tabKey: String): AgentThreadViewVirtualFile? {
    return pendingFilesByProviderAndPathAndTabKey[provider]?.get(normalizedPath)?.get(tabKey)
  }

  fun findConcreteFile(provider: AgentSessionProvider, normalizedPath: String, tabKey: String): AgentThreadViewVirtualFile? {
    return concreteFilesByProviderAndPathAndTabKey[provider]?.get(normalizedPath)?.get(tabKey)
  }

  fun findOpenTopLevelConcreteEntry(
    normalizedPath: String,
    provider: AgentSessionProvider,
    threadId: String,
  ): AgentThreadViewOpenFileEntry? {
    return entries.firstOrNull { entry ->
      entry.normalizedProjectPath == normalizedPath &&
      !entry.file.isPendingThread &&
      entry.file.subAgentId == null &&
      entry.file.provider == provider &&
      entry.file.threadId == threadId
    }
  }

  fun addContextTargetCandidates(normalizedPath: String): List<AgentPromptAddContextTargetCandidate> {
    val candidatesByIdentity = LinkedHashMap<Pair<AgentSessionProvider, String>, AgentPromptAddContextTargetCandidate>()
    for (entry in entries) {
      if (entry.normalizedProjectPath != normalizedPath) {
        continue
      }

      val threadViewFile = entry.file
      val provider = threadViewFile.provider ?: continue
      val threadId = threadViewFile.threadId.takeIf { id -> id.isNotBlank() } ?: continue
      if (threadViewFile.isPendingThread || threadViewFile.subAgentId != null) {
        continue
      }

      candidatesByIdentity.putIfAbsent(
        provider to threadId,
        AgentPromptAddContextTargetCandidate(
          projectPath = normalizedPath,
          provider = provider,
          launchMode = parseAgentThreadViewLaunchMode(threadViewFile.launchMode),
          threadId = threadId,
          displayText = threadViewFile.threadTitle.takeIf { title -> title.isNotBlank() } ?: threadId,
          secondaryText = "  ${provider.value}",
          selected = selectedTopLevelConcreteThreadViewTab == AgentThreadViewSelectedTopLevelConcreteTab(
            normalizedProjectPath = normalizedPath,
            provider = provider,
            threadId = threadId,
          ),
        )
      )
    }

    if (candidatesByIdentity.isEmpty()) {
      return emptyList()
    }
    val (selectedCandidates, otherCandidates) = candidatesByIdentity.values.partition { candidate -> candidate.selected }
    return selectedCandidates + otherCandidates
  }

  fun managersFor(file: AgentThreadViewVirtualFile): Set<FileEditorManagerEx> {
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

  fun isTopLevelConcreteThreadIdentityOpen(
    normalizedPath: String,
    threadIdentity: String,
  ): Boolean {
    return topLevelConcreteThreadIdentitiesByPathAndManager[normalizedPath].orEmpty().values.any { identities ->
      threadIdentity in identities
    }
  }

  fun closeTopLevelConcreteTabs(
    normalizedPath: String,
    provider: AgentSessionProvider,
    threadIdentity: String,
  ): Int {
    var closedTabs = 0
    for (entry in entries) {
      val threadViewFile = entry.file
      if (
        entry.normalizedProjectPath == normalizedPath &&
        threadViewFile.provider == provider &&
        !threadViewFile.isPendingThread &&
        threadViewFile.subAgentId == null &&
        threadViewFile.threadIdentity == threadIdentity &&
        closedTopLevelConcreteEntries.add(entry)
      ) {
        entry.manager.closeFile(threadViewFile)
        val manager = entry.manager as? FileEditorManagerEx
        if (manager != null) {
          concreteThreadIdentitiesByPathAndManager[normalizedPath]?.get(manager)?.remove(threadIdentity)
          topLevelConcreteThreadIdentitiesByPathAndManager[normalizedPath]?.get(manager)?.remove(threadIdentity)
        }
        closedTabs++
      }
    }
    return closedTabs
  }

  fun recordConcreteThreadIdentityOpen(
    normalizedPath: String,
    managers: Set<FileEditorManagerEx>,
    threadIdentity: String,
  ) {
    recordConcreteThreadIdentity(concreteThreadIdentitiesByPathAndManager, normalizedPath, managers, threadIdentity)
    recordConcreteThreadIdentity(topLevelConcreteThreadIdentitiesByPathAndManager, normalizedPath, managers, threadIdentity)
  }

  fun replaceConcreteThreadIdentity(
    normalizedPath: String,
    managers: Set<FileEditorManagerEx>,
    previousIdentity: String,
    threadIdentity: String,
  ) {
    replaceConcreteThreadIdentity(
      identitiesByManager = concreteThreadIdentitiesByPathAndManager,
      normalizedPath = normalizedPath,
      managers = managers,
      previousIdentity = previousIdentity,
      threadIdentity = threadIdentity,
    )
    replaceConcreteThreadIdentity(
      identitiesByManager = topLevelConcreteThreadIdentitiesByPathAndManager,
      normalizedPath = normalizedPath,
      managers = managers,
      previousIdentity = previousIdentity,
      threadIdentity = threadIdentity,
    )
  }

  fun closeMatchingOpenTabs(projectPath: String, threadIdentity: String, subAgentId: String?): Int {
    var closedTabs = 0
    for (entry in entries) {
      val threadViewFile = entry.file
      if (
        entry.normalizedProjectPath == projectPath &&
        threadViewFile.threadIdentity == threadIdentity &&
        (subAgentId == null || threadViewFile.subAgentId == subAgentId)
      ) {
        entry.manager.closeFile(threadViewFile)
        closedTabs++
      }
    }
    return closedTabs
  }

  fun toRefreshSnapshot(): AgentThreadViewOpenTabsRefreshSnapshot {
    val pendingTabsByProvider = LinkedHashMap<AgentSessionProvider, Map<String, List<AgentThreadViewPendingTabSnapshot>>>(
      pendingFilesByProviderAndPathAndTabKey.size
    )
    for (provider in pendingFilesByProviderAndPathAndTabKey.keys) {
      pendingTabsByProvider[provider] = pendingTabsByPath(provider)
    }

    val concreteTabsByProvider = LinkedHashMap<AgentSessionProvider, Map<String, List<AgentThreadViewConcreteTabSnapshot>>>(
      concreteFilesByProviderAndPathAndTabKey.size
    )
    for (provider in concreteFilesByProviderAndPathAndTabKey.keys) {
      concreteTabsByProvider[provider] = concreteTabsAwaitingNewThreadRebindByPath(provider)
    }

    return AgentThreadViewOpenTabsRefreshSnapshot(
      openProjectPaths = LinkedHashSet(openProjectPaths),
      projectPathAliasesByPath = projectPathAliasesSnapshot(),
      selectedThreadViewThreadIdentity = selectedThreadViewThreadIdentity,
      pendingTabsByProvider = pendingTabsByProvider,
      concreteTabsAwaitingNewThreadRebindByProvider = concreteTabsByProvider,
      concreteThreadIdentitiesByPath = concreteThreadIdentitiesByPath(),
    )
  }

  private fun projectPathAliasesSnapshot(): Map<String, Set<String>> {
    val result = LinkedHashMap<String, Set<String>>(projectPathAliasesByPath.size)
    for ((normalizedPath, aliases) in projectPathAliasesByPath) {
      result[normalizedPath] = LinkedHashSet(aliases)
    }
    return result
  }
}

private fun recordProjectPathAlias(
  projectPathAliasesByPath: LinkedHashMap<String, LinkedHashSet<String>>,
  normalizedProjectPath: String,
  projectDirectory: String?,
) {
  val aliases = projectPathAliasesByPath.computeIfAbsent(normalizedProjectPath) { LinkedHashSet() }
  aliases.add(normalizedProjectPath)
  projectDirectory
    ?.let(::normalizeAgentWorkbenchPath)
    ?.takeIf { it.isNotBlank() }
    ?.let(aliases::add)
}

private fun recordConcreteThreadIdentity(
  identitiesByManager: ConcreteThreadIdentitiesByManager,
  normalizedPath: String,
  managers: Set<FileEditorManagerEx>,
  threadIdentity: String,
) {
  for (manager in managers) {
    identitiesByManager
      .computeIfAbsent(normalizedPath) { LinkedHashMap() }
      .computeIfAbsent(manager) { LinkedHashSet() }
      .add(threadIdentity)
  }
}

private fun replaceConcreteThreadIdentity(
  identitiesByManager: ConcreteThreadIdentitiesByManager,
  normalizedPath: String,
  managers: Set<FileEditorManagerEx>,
  previousIdentity: String,
  threadIdentity: String,
) {
  for (manager in managers) {
    identitiesByManager[normalizedPath]?.get(manager)?.remove(previousIdentity)
    identitiesByManager
      .computeIfAbsent(normalizedPath) { LinkedHashMap() }
      .computeIfAbsent(manager) { LinkedHashSet() }
      .add(threadIdentity)
  }
}

internal fun isPendingThreadIdentityForProvider(threadIdentity: String, provider: AgentSessionProvider): Boolean {
  val identity = splitAgentThreadIdentity(threadIdentity) ?: return false
  return provider.value.equals(identity.first, ignoreCase = true) && isAgentSessionPendingThreadId(identity.second)
}

internal fun pendingProviderForThreadIdentity(threadIdentity: String): AgentSessionProvider? {
  val identity = splitAgentThreadIdentity(threadIdentity) ?: return null
  if (!isAgentSessionPendingThreadId(identity.second)) {
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
