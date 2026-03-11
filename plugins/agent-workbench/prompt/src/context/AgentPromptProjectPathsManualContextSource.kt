// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncation
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptManualContextPickerRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptManualContextSourceBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayload
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayloadValue
import com.intellij.agent.workbench.sessions.core.prompt.array
import com.intellij.agent.workbench.sessions.core.prompt.objOrNull
import com.intellij.agent.workbench.sessions.core.prompt.string
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

private const val MAX_INCLUDED_SELECTION_PATHS = 20

internal const val MANUAL_PROJECT_PATHS_SOURCE_ID = "manual.project.paths"

private const val MANUAL_PROJECT_PATHS_SOURCE = "manualPaths"

private val LOG = logger<AgentPromptProjectPathsManualContextSource>()

internal data class ManualPathSelectionEntry(
  @JvmField val path: String,
  @JvmField val isDirectory: Boolean,
)

internal class ManualPathSelectionState(initialSelection: List<ManualPathSelectionEntry> = emptyList()) {
  private val selectionByPath = LinkedHashMap<String, ManualPathSelectionEntry>()

  init {
    replaceAll(initialSelection)
  }

  fun snapshot(): List<ManualPathSelectionEntry> = selectionByPath.values.toList()

  fun size(): Int = selectionByPath.size

  fun contains(path: String): Boolean = selectionByPath.containsKey(path)

  fun addTreeSelection(selection: Collection<ManualPathSelectionEntry>) {
    merge(selection)
  }

  fun addSearchSelection(selection: Collection<ManualPathSelectionEntry>) {
    merge(selection)
  }

  private fun replaceAll(selection: List<ManualPathSelectionEntry>) {
    selectionByPath.clear()
    normalizeManualPathSelection(selection).forEach { entry ->
      selectionByPath[entry.path] = entry
    }
  }

  private fun merge(selection: Collection<ManualPathSelectionEntry>) {
    normalizeManualPathSelection(selection.toList()).forEach { entry ->
      selectionByPath.putIfAbsent(entry.path, entry)
    }
  }
}

internal class AgentPromptProjectPathsManualContextSource : AgentPromptManualContextSourceBridge {
  override val sourceId: String
    get() = MANUAL_PROJECT_PATHS_SOURCE_ID

  override val order: Int
    get() = 10

  override fun getDisplayName(): String {
    return AgentPromptBundle.message("manual.context.paths.display.name")
  }

  override fun showPicker(request: AgentPromptManualContextPickerRequest) {
    try {
      val scopedRoots = resolveScopedProjectRoots(request.sourceProject, request.workingProjectPath)
      if (scopedRoots.isEmpty()) {
        request.onError(AgentPromptBundle.message("manual.context.paths.error.empty"))
        return
      }

      showProjectPathsChooserPopup(
        project = request.sourceProject,
        scopedRoots = scopedRoots,
        initialSelection = filterManualPathSelectionToScopedRoots(
          selection = extractCurrentPaths(request.currentItem),
          scopedRootPaths = scopedRoots.map(VirtualFile::getPath),
        ),
        anchorComponent = request.anchorComponent,
      ) { selection ->
        request.onSelected(buildManualPathsContextItem(selection))
      }
    }
    catch (error: Throwable) {
      LOG.warn(error)
      request.onError(AgentPromptBundle.message("manual.context.paths.error.load"))
    }
  }
}

@Suppress("DuplicatedCode")
internal fun buildManualPathsContextItem(selection: List<ManualPathSelectionEntry>): AgentPromptContextItem {
  val normalizedSelection = normalizeManualPathSelection(selection)
  val included = normalizedSelection.take(MAX_INCLUDED_SELECTION_PATHS)
  val fullContent = normalizedSelection.joinToString(separator = "\n") { entry ->
    if (entry.isDirectory) {
      "dir: ${entry.path}"
    }
    else {
      "file: ${entry.path}"
    }
  }
  val content = included.joinToString(separator = "\n") { entry ->
    if (entry.isDirectory) {
      "dir: ${entry.path}"
    }
    else {
      "file: ${entry.path}"
    }
  }
  val directoryCount = included.count(ManualPathSelectionEntry::isDirectory)
  val payloadEntries = included.map { entry ->
    AgentPromptPayload.obj(
      "kind" to AgentPromptPayload.str(if (entry.isDirectory) "dir" else "file"),
      "path" to AgentPromptPayload.str(entry.path),
    )
  }
  return AgentPromptContextItem(
    rendererId = AgentPromptContextRendererIds.PATHS,
    title = AgentPromptBundle.message("manual.context.paths.title"),
    body = content,
    payload = AgentPromptPayload.obj(
      "entries" to AgentPromptPayloadValue.Arr(payloadEntries),
      "selectedCount" to AgentPromptPayload.num(normalizedSelection.size),
      "includedCount" to AgentPromptPayload.num(included.size),
      "directoryCount" to AgentPromptPayload.num(directoryCount),
      "fileCount" to AgentPromptPayload.num(included.size - directoryCount),
    ),
    itemId = MANUAL_PROJECT_PATHS_SOURCE_ID,
    source = MANUAL_PROJECT_PATHS_SOURCE,
    truncation = AgentPromptContextTruncation(
      originalChars = fullContent.length,
      includedChars = content.length,
      reason = if (normalizedSelection.size > included.size) {
        AgentPromptContextTruncationReason.SOURCE_LIMIT
      }
      else {
        AgentPromptContextTruncationReason.NONE
      },
    ),
  )
}

internal fun normalizeManualPathSelection(selection: List<ManualPathSelectionEntry>): List<ManualPathSelectionEntry> {
  if (selection.isEmpty()) {
    return emptyList()
  }

  val unique = LinkedHashMap<String, ManualPathSelectionEntry>()
  selection.forEach { entry ->
    val normalizedPath = entry.path.trim().takeIf { it.isNotEmpty() } ?: return@forEach
    unique.putIfAbsent(normalizedPath, entry.copy(path = normalizedPath))
  }
  return unique.values.toList()
}

internal fun removeManualPathSelection(
  selection: List<ManualPathSelectionEntry>,
  removedSelection: Collection<ManualPathSelectionEntry>,
): List<ManualPathSelectionEntry> {
  val removedPaths = normalizeManualPathSelection(removedSelection.toList())
    .map(ManualPathSelectionEntry::path)
    .toHashSet()
  if (removedPaths.isEmpty()) {
    return normalizeManualPathSelection(selection)
  }
  return normalizeManualPathSelection(selection)
    .filterNot { entry -> entry.path in removedPaths }
}

internal fun extractCurrentPaths(item: AgentPromptContextItem?): List<ManualPathSelectionEntry> {
  return item?.payload
    ?.objOrNull()
    ?.array("entries")
    ?.mapNotNull { value ->
      val entry = value.objOrNull() ?: return@mapNotNull null
      val path = entry.string("path")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
      ManualPathSelectionEntry(
        path = path,
        isDirectory = entry.string("kind") == "dir",
      )
    }
    .orEmpty()
}

internal fun resolveScopedContentRootPaths(
  contentRootPaths: Collection<String>,
  workingProjectPath: String?,
): Set<String> {
  if (workingProjectPath.isNullOrBlank()) {
    return LinkedHashSet(contentRootPaths)
  }
  val matchesContent = contentRootPaths.any { contentRootPath ->
    FileUtil.isAncestor(contentRootPath, workingProjectPath, false) || FileUtil.pathsEqual(contentRootPath, workingProjectPath)
  }
  return if (matchesContent) linkedSetOf(workingProjectPath) else LinkedHashSet(contentRootPaths)
}

internal fun filterManualPathSelectionToScopedRoots(
  selection: List<ManualPathSelectionEntry>,
  scopedRootPaths: Collection<String>,
): List<ManualPathSelectionEntry> {
  return normalizeManualPathSelection(selection)
    .filter { entry -> isUnderAnyRoot(entry.path, scopedRootPaths) }
}

private fun resolveScopedProjectRoots(
  project: Project,
  workingProjectPath: String?,
): List<VirtualFile> {
  return runReadActionBlocking {
    val contentRoots = ProjectRootManager.getInstance(project).contentRootsFromAllModules
      .distinctBy { it.path }
    if (contentRoots.isEmpty()) {
      return@runReadActionBlocking emptyList()
    }

    val scopedRootPaths = resolveScopedContentRootPaths(contentRoots.map { it.path }, workingProjectPath)
    val fileIndex = ProjectFileIndex.getInstance(project)
    val resolvedRoots = ArrayList<VirtualFile>(scopedRootPaths.size)
    scopedRootPaths.forEach { path ->
      val root = contentRoots.firstOrNull { contentRoot -> FileUtil.pathsEqual(contentRoot.path, path) }
                 ?: LocalFileSystem.getInstance().findFileByPath(path)
      val scopedRoot = when {
        root == null -> null
        root.isDirectory -> root
        else -> root.parent
      }
      if (scopedRoot != null && scopedRoot.isInLocalFileSystem && fileIndex.isInContent(scopedRoot)) {
        resolvedRoots += scopedRoot
      }
    }
    resolvedRoots.ifEmpty { contentRoots.toList() }
  }
}

internal fun isUnderAnyRoot(path: String, rootPaths: Collection<String>): Boolean {
  return rootPaths.any { rootPath ->
    FileUtil.isAncestor(rootPath, path, false) || FileUtil.pathsEqual(rootPath, path)
  }
}
