// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorPhase
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.prompt.vcs.AgentPromptVcsBundle
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.platform.vcs.changes.ChangesUtil

private const val MAX_CHANGELISTS = 10
private const val MAX_CHANGES_PER_CHANGELIST = 50
private const val MAX_CHANGES_FOR_FILE_SELECTION = 50

internal class AgentPromptChangesTreeContextContributor : AgentPromptContextContributorBridge {
  override val phase: AgentPromptContextContributorPhase
    get() = AgentPromptContextContributorPhase.INVOCATION

  override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
    val dataContext = invocationData.dataContextOrNull() ?: return emptyList()

    val changeLists = VcsDataKeys.CHANGE_LISTS.getData(dataContext)
    if (!changeLists.isNullOrEmpty()) {
      return collectChangeLists(changeLists, invocationData)
    }

    val leadSelection = VcsDataKeys.CHANGE_LEAD_SELECTION.getData(dataContext)
    if (!leadSelection.isNullOrEmpty()) {
      return collectChanges(leadSelection, invocationData)
    }

    val changes = VcsDataKeys.SELECTED_CHANGES.getData(dataContext)
    if (!changes.isNullOrEmpty()) {
      return collectChanges(changes, invocationData)
    }

    return emptyList()
  }

  private fun collectChangeLists(
    changeLists: Array<ChangeList>,
    invocationData: AgentPromptInvocationData,
  ): List<AgentPromptContextItem> {
    val basePath = invocationData.project.basePath
    val totalCount = changeLists.size
    val included = changeLists.take(MAX_CHANGELISTS)

    val bodyBuilder = StringBuilder()
    val payloadEntries = mutableListOf<AgentPromptPayloadValue>()
    for ((index, changeList) in included.withIndex()) {
      if (index > 0) bodyBuilder.append("\n\n")

      val isDefault = (changeList as? LocalChangeList)?.isDefault == true
      bodyBuilder.append("Changelist: ").append(changeList.name)
      if (isDefault) bodyBuilder.append(" (active)")

      val comment = changeList.comment?.trim()
      if (!comment.isNullOrEmpty()) {
        bodyBuilder.append("\nComment: ").append(comment)
      }

      val changes = changeList.changes.toList()
      if (changes.isEmpty()) {
        bodyBuilder.append("\nChanges: none")
      }
      else {
        val includedChanges = changes.take(MAX_CHANGES_PER_CHANGELIST)
        bodyBuilder.append("\nChanges (").append(changes.size).append("):")
        for (change in includedChanges) {
          bodyBuilder.append("\n- ").append(formatChange(change, basePath))
        }
        if (changes.size > includedChanges.size) {
          bodyBuilder.append("\n... and ").append(changes.size - includedChanges.size).append(" more")
        }
      }

      payloadEntries.add(buildChangeListPayload(changeList, basePath))
    }

    val fullBodyBuilder = StringBuilder(bodyBuilder)
    if (totalCount > included.size) {
      fullBodyBuilder.append("\n\n... and ").append(totalCount - included.size).append(" more changelists")
    }

    val body = bodyBuilder.toString()
    val fullBody = fullBodyBuilder.toString()
    val truncated = totalCount > included.size

    val payload = AgentPromptPayload.obj(
      "kind" to AgentPromptPayload.str("changelists"),
      "entries" to AgentPromptPayloadValue.Arr(payloadEntries),
      "selectedCount" to AgentPromptPayload.num(totalCount),
      "includedCount" to AgentPromptPayload.num(included.size),
    )

    return listOf(
      AgentPromptContextItem(
        rendererId = AgentPromptContextRendererIds.SNIPPET,
        title = AgentPromptVcsBundle.message("context.changes.title"),
        body = body,
        payload = payload,
        itemId = "changes.selection",
        source = "changes",
        truncation = AgentPromptContextTruncation(
          originalChars = fullBody.length,
          includedChars = body.length,
          reason = if (truncated) AgentPromptContextTruncationReason.SOURCE_LIMIT else AgentPromptContextTruncationReason.NONE,
        ),
      ),
    )
  }

  private fun collectChanges(
    changes: Array<Change>,
    invocationData: AgentPromptInvocationData,
  ): List<AgentPromptContextItem> {
    val basePath = invocationData.project.basePath
    val unique = changes.distinctBy { change -> ChangesUtil.getFilePath(change).path }
    val totalCount = unique.size
    val included = unique.take(MAX_CHANGES_FOR_FILE_SELECTION)

    val bodyBuilder = StringBuilder("Selected changes:")
    for (change in included) {
      bodyBuilder.append("\n- ").append(formatChange(change, basePath))
    }

    val fullBodyBuilder = StringBuilder(bodyBuilder)
    if (totalCount > included.size) {
      fullBodyBuilder.append("\n... and ").append(totalCount - included.size).append(" more")
    }

    val body = bodyBuilder.toString()
    val fullBody = fullBodyBuilder.toString()
    val truncated = totalCount > included.size

    val payloadEntries = included.map { change -> buildChangePayload(change, basePath) }
    val payload = AgentPromptPayload.obj(
      "kind" to AgentPromptPayload.str("changes"),
      "entries" to AgentPromptPayloadValue.Arr(payloadEntries),
      "selectedCount" to AgentPromptPayload.num(totalCount),
      "includedCount" to AgentPromptPayload.num(included.size),
    )

    return listOf(
      AgentPromptContextItem(
        rendererId = AgentPromptContextRendererIds.SNIPPET,
        title = AgentPromptVcsBundle.message("context.changes.title"),
        body = body,
        payload = payload,
        itemId = "changes.selection",
        source = "changes",
        truncation = AgentPromptContextTruncation(
          originalChars = fullBody.length,
          includedChars = body.length,
          reason = if (truncated) AgentPromptContextTruncationReason.SOURCE_LIMIT else AgentPromptContextTruncationReason.NONE,
        ),
      ),
    )
  }

  private fun buildChangeListPayload(changeList: ChangeList, basePath: String?): AgentPromptPayloadValue {
    val isDefault = (changeList as? LocalChangeList)?.isDefault == true
    val comment = changeList.comment?.trim()
    val changes = changeList.changes.toList().take(MAX_CHANGES_PER_CHANGELIST)

    val fields = mutableListOf(
      "name" to AgentPromptPayload.str(changeList.name),
      "isDefault" to AgentPromptPayload.bool(isDefault),
    )
    if (!comment.isNullOrEmpty()) {
      fields.add("comment" to AgentPromptPayload.str(comment))
    }
    fields.add("changeCount" to AgentPromptPayload.num(changeList.changes.size))
    fields.add("changes" to AgentPromptPayloadValue.Arr(changes.map { buildChangePayload(it, basePath) }))
    return AgentPromptPayload.obj(*fields.toTypedArray())
  }

  private fun buildChangePayload(change: Change, basePath: String?): AgentPromptPayloadValue {
    val filePath = ChangesUtil.getFilePath(change)
    val path = relativizePath(filePath.path, basePath)
    return AgentPromptPayload.obj(
      "path" to AgentPromptPayload.str(path),
      "type" to AgentPromptPayload.str(changeTypeName(change.type)),
    )
  }
}

private fun formatChange(change: Change, basePath: String?): String {
  val filePath = ChangesUtil.getFilePath(change)
  val path = relativizePath(filePath.path, basePath)
  return "${changeTypeName(change.type)}: $path"
}

private fun changeTypeName(type: Change.Type): String {
  return when (type) {
    Change.Type.MODIFICATION -> "modified"
    Change.Type.NEW -> "added"
    Change.Type.DELETED -> "deleted"
    Change.Type.MOVED -> "moved"
  }
}

private fun relativizePath(absolutePath: String, basePath: String?): String {
  if (basePath != null && absolutePath.startsWith(basePath)) {
    val relative = absolutePath.removePrefix(basePath).removePrefix("/")
    if (relative.isNotEmpty()) return relative
  }
  return absolutePath
}
