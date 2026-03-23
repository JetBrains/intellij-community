// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorPhase
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import javax.swing.JTree

private const val MAX_INCLUDED_SELECTION_PATHS = 5

internal class AgentPromptProjectViewSelectionContextContributor : AgentPromptContextContributorBridge {
  override val phase: AgentPromptContextContributorPhase
    get() = AgentPromptContextContributorPhase.INVOCATION

  override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
    val selectedFiles = extractSelectedFiles(invocationData) ?: return emptyList()
    if (selectedFiles.isEmpty()) {
      return emptyList()
    }

    val uniqueSelection = LinkedHashMap<String, Boolean>()
    for (file in selectedFiles) {
      if (!file.isInLocalFileSystem) {
        continue
      }
      val path = file.path
      if (path == ".") {
        continue
      }
      uniqueSelection.putIfAbsent(path, file.isDirectory)
    }
    if (uniqueSelection.isEmpty()) {
      return emptyList()
    }

    val totalSelected = uniqueSelection.size
    val fullContent = uniqueSelection.entries.joinToString(separator = "\n") { (path, isDirectory) ->
      if (isDirectory) {
        "dir: $path"
      }
      else {
        "file: $path"
      }
    }
    val included = uniqueSelection.entries
      .take(MAX_INCLUDED_SELECTION_PATHS)
    val content = included.joinToString(separator = "\n") { (path, isDirectory) ->
      if (isDirectory) {
        "dir: $path"
      }
      else {
        "file: $path"
      }
    }
    if (content.isBlank()) {
      return emptyList()
    }

    val truncatedBySelection = totalSelected > included.size
    val directoryCount = included.count { it.value }
    val fileCount = included.size - directoryCount
    val payloadEntries = included.map { (path, isDirectory) ->
      AgentPromptPayload.obj(
        "kind" to AgentPromptPayload.str(if (isDirectory) "dir" else "file"),
        "path" to AgentPromptPayload.str(path),
      )
    }
    val payload = AgentPromptPayload.obj(
      "entries" to AgentPromptPayloadValue.Arr(payloadEntries),
      "selectedCount" to AgentPromptPayload.num(totalSelected),
      "includedCount" to AgentPromptPayload.num(included.size),
      "directoryCount" to AgentPromptPayload.num(directoryCount),
      "fileCount" to AgentPromptPayload.num(fileCount),
    )

    return listOf(
      AgentPromptContextItem(
        rendererId = AgentPromptContextRendererIds.PATHS,
        title = AgentPromptBundle.message("context.paths.title"),
        body = content,
        payload = payload,
        itemId = "projectView.selection",
        source = "projectView",
        truncation = AgentPromptContextTruncation(
          originalChars = fullContent.length,
          includedChars = content.length,
          reason = if (truncatedBySelection) {
            AgentPromptContextTruncationReason.SOURCE_LIMIT
          }
          else {
            AgentPromptContextTruncationReason.NONE
          },
        ),
      )
    )
  }

  private fun extractSelectedFiles(invocationData: AgentPromptInvocationData): List<VirtualFile>? {
    val dataContext = invocationData.dataContextOrNull() ?: return null

    // When the context component is a JTree outside the Project View (e.g., Changes tree),
    // skip — the tree selection contributor will provide richer context.
    val component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)
    if (component is JTree) {
      val toolWindow = PlatformDataKeys.TOOL_WINDOW.getData(dataContext)
      if (toolWindow?.id != ToolWindowId.PROJECT_VIEW) {
        return null
      }
    }

    val selectedArray = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)
    if (!selectedArray.isNullOrEmpty()) {
      return selectedArray.toList()
    }

    val selectedSingle = CommonDataKeys.VIRTUAL_FILE.getData(dataContext)
    if (selectedSingle != null) {
      return listOf(selectedSingle)
    }

    return null
  }
}
