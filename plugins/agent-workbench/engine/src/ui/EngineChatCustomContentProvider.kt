// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.ui

import com.intellij.agent.workbench.chat.AgentChatCustomContentProvider
import com.intellij.agent.workbench.chat.AgentChatContentContext
import com.intellij.platform.ai.agent.core.parseAgentThreadIdentity
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.engine.core.ThreadId
import com.intellij.platform.ai.agent.sessions.core.launch.AGENT_SESSION_SURFACE_ACP
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

/**
 * Routes Engine-backed chat tabs to the IDE-native [AgentAcpThreadScreen] instead of an embedded
 * terminal, so ACP/remote/mock threads render their session UI in the same Agent Chat editor tab.
 */
internal class EngineChatCustomContentProvider : AgentChatCustomContentProvider {
  override val provider: AgentSessionProvider
    get() = ACP_PROVIDER

  override fun handles(context: AgentChatContentContext): Boolean {
    return context.provider == provider && context.surfaceId == AGENT_SESSION_SURFACE_ACP
  }

  override fun createComponent(
    project: Project,
    threadIdentity: String,
    threadId: String,
    parent: Disposable,
  ): JComponent {
    return AgentAcpThreadScreen(project, ThreadId(resolveThreadId(threadId, threadIdentity)), parent)
  }

  /** The Engine keys by the raw thread id; tolerate a "<provider>:<threadId>" identity from the tab. */
  private fun resolveThreadId(threadId: String, threadIdentity: String): String {
    if (threadId.isNotBlank()) return threadId
    return parseAgentThreadIdentity(threadIdentity)?.threadId ?: threadIdentity
  }
}
