// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextPickerRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextSourceBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.prompt.core.array
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.core.string
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
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
      val scopedRootPaths = resolvePickerBrowseRootPaths(request.sourceProject)
      if (scopedRootPaths.isEmpty()) {
        request.onError(AgentPromptBundle.message("manual.context.paths.error.empty"))
        return
      }

      val currentSelection = extractCurrentPaths(request.currentItem)
      val initialSelection = resolveInitialManualPathSelection(
        selection = currentSelection,
        scopedRootPaths = scopedRootPaths,
      )

      showProjectPathsChooserPopup(
        project = request.sourceProject,
        scopedRootPaths = scopedRootPaths,
        initialSelection = initialSelection,
        initialTreePreselection = resolveInitialTreePreselection(
          initialSelection = initialSelection,
          invocationData = request.invocationData,
          scopedRootPaths = scopedRootPaths,
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

internal fun resolvePickerBrowseRootPaths(
  contentRootPaths: Collection<String>,
  scratchRootPaths: Collection<String>,
): List<String> {
  val rootPaths = LinkedHashSet<String>()
  rootPaths.addAll(contentRootPaths)
  rootPaths.addAll(scratchRootPaths)
  return rootPaths.toList()
}

internal fun filterManualPathSelectionToScopedRoots(
  selection: List<ManualPathSelectionEntry>,
  scopedRootPaths: Collection<String>,
): List<ManualPathSelectionEntry> {
  return normalizeManualPathSelection(selection)
    .filter { entry -> isUnderAnyRoot(entry.path, scopedRootPaths) }
}

internal fun resolveInitialManualPathSelection(
  selection: List<ManualPathSelectionEntry>,
  scopedRootPaths: Collection<String>,
): List<ManualPathSelectionEntry> {
  return filterManualPathSelectionToScopedRoots(selection, scopedRootPaths)
}

internal fun resolveInitialTreePreselection(
  initialSelection: List<ManualPathSelectionEntry>,
  invocationData: AgentPromptInvocationData,
  scopedRootPaths: Collection<String>,
): ManualPathSelectionEntry? {
  if (initialSelection.isNotEmpty()) {
    return null
  }
  return extractCurrentManualPathSelection(invocationData, scopedRootPaths)
}

internal fun extractCurrentManualPathSelection(
  invocationData: AgentPromptInvocationData,
  scopedRootPaths: Collection<String>,
): ManualPathSelectionEntry? {
  val dataContext = invocationData.dataContextOrNull() ?: return null
  CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)
    .orEmpty()
    .firstNotNullOfOrNull { virtualFile -> currentManualPathSelectionEntry(virtualFile, scopedRootPaths) }
    ?.let { return it }

  return CommonDataKeys.VIRTUAL_FILE.getData(dataContext)
    ?.let { virtualFile -> currentManualPathSelectionEntry(virtualFile, scopedRootPaths) }
}

private fun currentManualPathSelectionEntry(
  virtualFile: VirtualFile,
  scopedRootPaths: Collection<String>,
): ManualPathSelectionEntry? {
  if (!virtualFile.isInLocalFileSystem || !isUnderAnyRoot(virtualFile.path, scopedRootPaths)) {
    return null
  }
  return ManualPathSelectionEntry(path = virtualFile.path, isDirectory = virtualFile.isDirectory)
}

private fun resolvePickerBrowseRootPaths(
  project: Project,
): List<String> {
  return runReadActionBlocking {
    val contentRoots = ProjectRootManager.getInstance(project).contentRootsFromAllModules
      .distinctBy { it.path }
    val scratchRoots = resolveVisibleScratchRoots()
    resolvePickerBrowseRootPaths(
      contentRootPaths = contentRoots.map { it.path },
      scratchRootPaths = scratchRoots.map { it.path },
    )
  }
}

private fun resolveVisibleScratchRoots(): List<VirtualFile> {
  val scratchFileService = ScratchFileService.getInstance()
  return RootType.getAllRootTypes()
    .filterNot { it.isHidden }
    .mapNotNull(scratchFileService::getVirtualFile)
    .distinctBy { it.path }
}

internal fun isUnderAnyRoot(path: String, rootPaths: Collection<String>): Boolean {
  return rootPaths.any { rootPath ->
    FileUtil.isAncestor(rootPath, path, false) || FileUtil.pathsEqual(rootPath, path)
  }
}
