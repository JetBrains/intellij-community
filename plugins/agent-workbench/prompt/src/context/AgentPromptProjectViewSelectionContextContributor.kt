// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextContributorPhase
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile

private const val MAX_INCLUDED_SELECTION_PATHS = 5

internal class AgentPromptProjectViewSelectionContextContributor : AgentPromptContextContributorBridge {
  override val phase: AgentPromptContextContributorPhase
    get() = AgentPromptContextContributorPhase.INVOCATION

  override val order: Int
    get() = 100

  override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
    val selectedFiles = extractSelectedFiles(invocationData) ?: return emptyList()
    if (selectedFiles.isEmpty()) {
      return emptyList()
    }

    val uniqueSelection = LinkedHashMap<String, Boolean>()
    selectedFiles.forEach { file ->
      uniqueSelection.putIfAbsent(file.path, file.isDirectory)
    }
    if (uniqueSelection.isEmpty()) {
      return emptyList()
    }

    val totalSelected = uniqueSelection.size
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

    val directoryCount = included.count { it.value }
    val fileCount = included.size - directoryCount
    val metadata = linkedMapOf(
      "source" to "projectView",
      "selectedCount" to totalSelected.toString(),
      "includedCount" to included.size.toString(),
      "truncated" to (totalSelected > included.size).toString(),
      "directoryCount" to directoryCount.toString(),
      "fileCount" to fileCount.toString(),
    )

    return listOf(
      AgentPromptContextItem(
        kindId = AgentPromptContextKinds.PATHS,
        title = AgentPromptBundle.message("context.paths.title"),
        content = content,
        metadata = metadata,
      )
    )
  }

  private fun extractSelectedFiles(invocationData: AgentPromptInvocationData): List<VirtualFile>? {
    val dataContext = invocationData.dataContextOrNull() ?: return null
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
