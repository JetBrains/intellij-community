// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.sessions.core.formatCompactAgentSessionTitle
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.HtmlChunk.br
import com.intellij.openapi.vfs.VirtualFile

internal class AgentThreadViewEditorTabTitleProvider @JvmOverloads constructor(
  private val isDedicatedProject: (Project) -> Boolean = ::isAgentWorkbenchDedicatedFrameProject,
) : EditorTabTitleProvider, DumbAware {
  override suspend fun getEditorTabTitleAsync(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    return getEditorTabTitle(project, file)
  }

  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    val threadViewFile = file as? AgentThreadViewVirtualFile ?: return null
    return formatCompactAgentSessionTitle(threadViewFile.threadTitle)
  }

  override fun getEditorTabTooltipHtml(project: Project, virtualFile: VirtualFile): HtmlChunk? {
    val threadViewFile = virtualFile as? AgentThreadViewVirtualFile ?: return null
    val sourceProjectPath = threadViewFile.projectPath.takeIf { it.isNotBlank() && isDedicatedProject(project) }
    if (sourceProjectPath == null) {
      return HtmlChunk.text(threadViewFile.threadTitle)
    }
    return HtmlChunk.html().children(
      HtmlChunk.text(threadViewFile.threadTitle),
      br(),
      HtmlChunk.text(AgentThreadViewBundle.message("thread.view.tab.tooltip.source.project", sourceProjectPath)),
    )
  }
}
