// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AgentChatEditorServiceLog

private val LOG = logger<AgentChatEditorServiceLog>()
private const val CODEX_PROVIDER_ID = "codex"

data class AgentChatPendingTabRebindTarget(
  @JvmField val threadIdentity: String,
  @JvmField val threadId: String,
  @JvmField val shellCommand: List<String>,
  @JvmField val threadTitle: String,
  @JvmField val threadActivity: AgentThreadActivity,
  @JvmField val threadUpdatedAt: Long = 0L,
)

data class AgentChatPendingCodexTabSnapshot(
  @JvmField val projectPath: String,
  @JvmField val pendingThreadIdentity: String,
  @JvmField val pendingCreatedAtMs: Long?,
  @JvmField val pendingFirstInputAtMs: Long?,
  @JvmField val pendingLaunchMode: String?,
)

suspend fun openChat(
  project: Project,
  projectPath: String,
  threadIdentity: String,
  shellCommand: List<String>,
  threadId: String,
  threadTitle: String,
  subAgentId: String?,
  threadActivity: AgentThreadActivity = AgentThreadActivity.READY,
  pendingCreatedAtMs: Long? = null,
  pendingFirstInputAtMs: Long? = null,
  pendingLaunchMode: String? = null,
) {
  val manager = FileEditorManagerEx.getInstanceExAsync(project)
  val existing = findExistingChat(manager.openFiles, threadIdentity, subAgentId)
  LOG.debug {
    "openChat(project=${project.name}, path=$projectPath, identity=$threadIdentity, " +
    "subAgentId=$subAgentId, existing=${existing != null}, title=$threadTitle)"
  }
  val tabsService = serviceAsync<AgentChatTabsService>()
  val fileSystem = agentChatVirtualFileSystem()
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
  )
  val file = existing ?: fileSystem.getOrCreateFile(snapshot)
  if (existing != null) {
    existing.updateCommandAndThreadId(shellCommand = shellCommand, threadId = threadId)
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
    tabsService.upsert(existing.toSnapshot())
    LOG.debug {
      "openChat existing tab update(identity=$threadIdentity, subAgentId=$subAgentId): " +
      "titleUpdated=$titleUpdated, activityUpdated=$activityUpdated, currentName=${existing.name}," +
      " currentTitle=${existing.threadTitle}, currentActivity=${existing.threadActivity}"
    }
    if (titleUpdated || activityUpdated || pendingUpdated) {
      manager.updateFilePresentation(existing)
    }
  }
  else {
    tabsService.upsert(file.toSnapshot())
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

suspend fun collectOpenAgentChatProjectPaths(): Set<String> {
  return collectOpenAgentChatProjectPaths(includePendingOnly = false)
}

suspend fun collectOpenPendingAgentChatProjectPaths(): Set<String> {
  return collectOpenAgentChatProjectPaths(includePendingOnly = true)
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
      result.getOrPut(normalizedPath) { ArrayList() }.add(
        AgentChatPendingCodexTabSnapshot(
          projectPath = normalizedPath,
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
      result.getOrPut(normalizedPath) { LinkedHashSet() }.add(chatFile.threadIdentity)
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

suspend fun rebindOpenAgentChatPendingTabs(
  targetsByProjectPath: Map<String, List<AgentChatPendingTabRebindTarget>>,
): Int {
  if (targetsByProjectPath.isEmpty()) {
    return 0
  }

  val tabsService = serviceAsync<AgentChatTabsService>()
  val updatedSnapshots = ArrayList<AgentChatTabSnapshot>()
  var reboundTabs: Int
  var updatedPresentations: Int
  withContext(Dispatchers.UI) {
    val managerByFile = LinkedHashMap<AgentChatVirtualFile, LinkedHashSet<FileEditorManagerEx>>()
    val openConcreteIdentitiesByPath = LinkedHashMap<String, LinkedHashSet<String>>()
    val pendingFilesByPath = LinkedHashMap<String, MutableList<AgentChatVirtualFile>>()

    for (project in ProjectManager.getInstance().openProjects) {
      val manager = project.serviceIfCreated<FileEditorManager>() as? FileEditorManagerEx ?: continue
      for (openFile in manager.openFiles) {
        val chatFile = openFile as? AgentChatVirtualFile ?: continue
        managerByFile.computeIfAbsent(chatFile) { LinkedHashSet() }.add(manager)
        val normalizedPath = normalizeAgentWorkbenchPath(chatFile.projectPath)
        if (isPendingThreadIdentity(chatFile.threadIdentity)) {
          pendingFilesByPath.computeIfAbsent(normalizedPath) { ArrayList() }.add(chatFile)
        }
        else {
          openConcreteIdentitiesByPath.computeIfAbsent(normalizedPath) { LinkedHashSet() }.add(chatFile.threadIdentity)
        }
      }
    }

    val changedFiles = LinkedHashSet<AgentChatVirtualFile>()
    for ((projectPath, pendingFiles) in pendingFilesByPath) {
      val targets = targetsByProjectPath[projectPath].orEmpty()
      if (targets.isEmpty()) {
        continue
      }

      val knownIdentities = openConcreteIdentitiesByPath.computeIfAbsent(projectPath) { LinkedHashSet() }
      var targetIndex = 0
      for (pendingFile in pendingFiles) {
        while (targetIndex < targets.size && targets[targetIndex].threadIdentity in knownIdentities) {
          targetIndex++
        }
        if (targetIndex >= targets.size) {
          break
        }

        val target = targets.get(targetIndex++)
        if (pendingFile.rebindPendingThread(
            threadIdentity = target.threadIdentity,
            shellCommand = target.shellCommand,
            threadId = target.threadId,
            threadTitle = target.threadTitle,
            threadActivity = target.threadActivity,
          )) {
          knownIdentities.add(target.threadIdentity)
          updatedSnapshots.add(pendingFile.toSnapshot())
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

  if (updatedSnapshots.isNotEmpty()) {
    for (snapshot in updatedSnapshots) {
      tabsService.upsert(snapshot)
    }
  }

  LOG.debug {
    "rebindOpenAgentChatPendingTabs reboundTabs=$reboundTabs, updatedPresentations=$updatedPresentations," +
    " requestedPaths=${targetsByProjectPath.size}"
  }
  return reboundTabs
}

fun rebindSpecificOpenPendingCodexTab(
  projectPath: String,
  pendingThreadIdentity: String,
  target: AgentChatPendingTabRebindTarget,
): Boolean {
  val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath)
  val tabsService = service<AgentChatTabsService>()
  val managerByFile = LinkedHashMap<AgentChatVirtualFile, LinkedHashSet<FileEditorManagerEx>>()
  val openConcreteIdentities = LinkedHashSet<String>()
  var pendingFileToRebind: AgentChatVirtualFile? = null

  for (project in ProjectManager.getInstance().openProjects) {
    val manager = project.serviceIfCreated<FileEditorManager>() as? FileEditorManagerEx ?: continue
    for (openFile in manager.openFiles) {
      val chatFile = openFile as? AgentChatVirtualFile ?: continue
      val path = normalizeAgentWorkbenchPath(chatFile.projectPath)
      if (path != normalizedProjectPath) {
        continue
      }
      managerByFile.computeIfAbsent(chatFile) { LinkedHashSet() }.add(manager)
      if (isPendingThreadIdentity(chatFile.threadIdentity)) {
        if (chatFile.threadIdentity == pendingThreadIdentity) {
          pendingFileToRebind = chatFile
        }
      }
      else {
        openConcreteIdentities.add(chatFile.threadIdentity)
      }
    }
  }

  val pendingFile = pendingFileToRebind ?: return false
  if (target.threadIdentity in openConcreteIdentities) {
    return false
  }
  val changed = pendingFile.rebindPendingThread(
    threadIdentity = target.threadIdentity,
    shellCommand = target.shellCommand,
    threadId = target.threadId,
    threadTitle = target.threadTitle,
    threadActivity = target.threadActivity,
  )
  if (!changed) {
    return false
  }

  tabsService.upsert(pendingFile.toSnapshot())
  val managers = managerByFile[pendingFile].orEmpty()
  for (manager in managers) {
    manager.updateFilePresentation(pendingFile)
  }
  return true
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
        managerByFile.getOrPut(chatFile) { LinkedHashSet() }.add(manager)
        val key = normalizeAgentWorkbenchPath(chatFile.projectPath) to chatFile.threadIdentity
        val targetTitle = titleByPathAndThreadIdentity[key]
        val targetActivity = activityByPathAndThreadIdentity[key]
        if (targetTitle == null && targetActivity == null) {
          continue
        }

        var presentationUpdated = false
        if (targetTitle != null && chatFile.updateThreadTitle(targetTitle)) {
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
