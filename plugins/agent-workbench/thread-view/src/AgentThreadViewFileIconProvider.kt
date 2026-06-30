// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.chromePresentationActivity
import com.intellij.platform.ai.agent.core.parseAgentThreadIdentity
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.ui.agentSessionThreadStatusIcon
import com.intellij.agent.workbench.ui.clearAgentSessionThreadStatusIconCacheForTests
import com.intellij.agent.workbench.ui.resolveAgentSessionThreadIcon
import com.intellij.agent.workbench.ui.withYoloModeBadge
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
import java.util.Locale
import javax.swing.Icon

@TestOnly
internal fun clearAgentThreadViewIconCacheForTests() {
  clearAgentSessionThreadStatusIconCacheForTests()
}

internal class AgentThreadViewFileIconProvider : FileIconProvider {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    val threadViewFile = file as? AgentThreadViewVirtualFile ?: return null
    val activityReport = resolveAgentThreadViewTabIconActivityReport(threadViewFile)
    val icon = threadIcon(threadViewFile, activityReport) ?: providerIcon(
      provider = threadViewFile.provider,
      activityReport = activityReport,
    )
    if (threadViewFile.pendingLaunchMode == AgentSessionLaunchMode.YOLO.name) {
      return withYoloModeBadge(icon)
    }
    return icon
  }
}

internal fun resolveAgentThreadViewTabIconActivity(file: AgentThreadViewVirtualFile): AgentThreadActivity {
  return resolveAgentThreadViewTabIconActivityReport(file).chromePresentationActivity()
}

private fun resolveAgentThreadViewTabIconActivityReport(file: AgentThreadViewVirtualFile): AgentThreadActivityReport {
  // Agent Thread View tab icons are chrome signals; row/session activity stays on AgentThreadViewVirtualFile.threadActivity.
  return resolveAgentThreadViewThreadPresentation(file).activityReport
}

private fun threadIcon(file: AgentThreadViewVirtualFile, activityReport: AgentThreadActivityReport): Icon? {
  val provider = file.provider ?: return null
  val threadId = file.threadId.ifBlank { file.sessionId }
  val baseIcon = resolveAgentSessionThreadIcon(file.projectPath, provider, threadId) ?: return null
  return agentSessionThreadStatusIcon(baseIcon, activityReport.chromePresentationActivity())
}

internal fun providerIcon(
  provider: AgentSessionProvider?,
  threadActivity: AgentThreadActivity = AgentThreadActivity.READY,
): Icon {
  return agentSessionThreadStatusIcon(provider, threadActivity)
}

internal fun providerIcon(
  provider: AgentSessionProvider?,
  activityReport: AgentThreadActivityReport,
): Icon {
  return agentSessionThreadStatusIcon(provider, activityReport)
}

internal fun providerIcon(
  threadIdentity: String,
  threadActivity: AgentThreadActivity = AgentThreadActivity.READY,
): Icon {
  val providerId = parseAgentThreadIdentity(threadIdentity)?.providerId ?: threadIdentity.substringBefore(':')
  val provider = AgentSessionProvider.fromOrNull(providerId.lowercase(Locale.ROOT))
  return providerIcon(provider = provider, threadActivity = threadActivity)
}
