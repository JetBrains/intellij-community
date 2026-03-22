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
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import javax.swing.JTree

private const val MAX_INCLUDED_NODES = 10

internal class AgentPromptTreeSelectionContextContributor : AgentPromptContextContributorBridge {
  override val phase: AgentPromptContextContributorPhase
    get() = AgentPromptContextContributorPhase.INVOCATION

  override val order: Int
    get() = 200

  override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
    val dataContext = invocationData.dataContextOrNull() ?: return emptyList()
    val component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)
    val tree = component as? JTree ?: return emptyList()

    val selectionPaths = tree.selectionPaths
    if (selectionPaths.isNullOrEmpty()) {
      return emptyList()
    }

    val treeKind = resolveTreeKind(dataContext, tree)

    val texts = LinkedHashSet<String>()
    for (path in selectionPaths) {
      val node = path.lastPathComponent ?: continue
      val text = node.toString().trim()
      if (text.isBlank()) continue
      texts.add(text)
    }
    if (texts.isEmpty()) {
      return emptyList()
    }

    val totalSelected = texts.size
    val included = texts.toList().take(MAX_INCLUDED_NODES)
    val fullContent = "Tree: $treeKind\nSelected:\n" + texts.joinToString(separator = "\n") { "- $it" }
    val content = "Tree: $treeKind\nSelected:\n" + included.joinToString(separator = "\n") { "- $it" }

    val truncated = totalSelected > included.size
    val payloadEntries = included.map { AgentPromptPayload.str(it) }
    val payload = AgentPromptPayload.obj(
      "entries" to AgentPromptPayloadValue.Arr(payloadEntries),
      "selectedCount" to AgentPromptPayload.num(totalSelected),
      "includedCount" to AgentPromptPayload.num(included.size),
      "treeKind" to AgentPromptPayload.str(treeKind),
    )

    return listOf(
      AgentPromptContextItem(
        rendererId = AgentPromptContextRendererIds.SNIPPET,
        title = AgentPromptBundle.message("context.tree.selection.title", treeKind),
        body = content,
        payload = payload,
        itemId = "tree.selection",
        source = "tree",
        truncation = AgentPromptContextTruncation(
          originalChars = fullContent.length,
          includedChars = content.length,
          reason = if (truncated) {
            AgentPromptContextTruncationReason.SOURCE_LIMIT
          }
          else {
            AgentPromptContextTruncationReason.NONE
          },
        ),
      ),
    )
  }

  private fun resolveTreeKind(
    dataContext: DataContext,
    tree: JTree,
  ): String {
    val accessibleName = tree.accessibleContext?.accessibleName
    if (!accessibleName.isNullOrBlank()) {
      return accessibleName
    }
    val toolWindow = PlatformDataKeys.TOOL_WINDOW.getData(dataContext)
    if (toolWindow != null) {
      return toolWindow.id
    }
    val componentName = tree.name
    if (!componentName.isNullOrBlank()) {
      return componentName
    }
    return "Tree"
  }
}
