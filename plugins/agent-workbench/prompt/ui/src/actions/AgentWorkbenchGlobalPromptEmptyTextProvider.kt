// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.agent.workbench.prompt.ui.emptyState.isInlineEmptyStatePromptEnabled
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextProvider
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextSink
import javax.swing.JComponent

internal class AgentWorkbenchGlobalPromptEmptyTextProvider : EditorEmptyTextProvider {
  override fun appendEmptyText(splitters: JComponent, sink: EditorEmptyTextSink) {
    // The inline empty-state composer supersedes this fallback hint when enabled.
    if (isInlineEmptyStatePromptEnabled()) {
      return
    }
    sink.appendActionWithShortcuts(
      action = AgentPromptBundle.message("action.AgentWorkbenchPrompt.OpenGlobalPalette.text"),
      actionId = AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID,
    )
  }
}
