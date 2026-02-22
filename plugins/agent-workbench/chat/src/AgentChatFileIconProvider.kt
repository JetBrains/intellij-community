// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.parseAgentThreadIdentity
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.agentSessionThreadStatusIcon
import com.intellij.agent.workbench.sessions.core.providers.clearAgentSessionThreadStatusIconCacheForTests
import com.intellij.agent.workbench.sessions.core.providers.withYoloModeBadge
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IconManager
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

@TestOnly
internal fun clearAgentChatIconCacheForTests() {
  clearAgentSessionThreadStatusIconCacheForTests()
}

@TestOnly
internal fun clearAgentChatIconCacheForTests() {
  ICON_CACHE.clear()
}

internal class AgentChatFileIconProvider : FileIconProvider {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    val chatFile = file as? AgentChatVirtualFile ?: return null
    val icon = providerIcon(provider = chatFile.provider, threadActivity = chatFile.threadActivity)
    if (chatFile.pendingLaunchMode == AgentSessionLaunchMode.YOLO.name) {
      return withYoloModeBadge(icon)
    }
    return icon
  }
}

internal fun providerIcon(
  provider: AgentSessionProvider?,
  threadActivity: AgentThreadActivity = AgentThreadActivity.READY,
): Icon {
  return agentSessionThreadStatusIcon(provider, threadActivity)
}

internal fun providerIcon(
  threadIdentity: String,
  threadActivity: AgentThreadActivity = AgentThreadActivity.READY,
): Icon {
  val providerId = parseAgentThreadIdentity(threadIdentity)?.providerId ?: threadIdentity.substringBefore(':')
  val provider = AgentSessionProvider.fromOrNull(providerId.lowercase(Locale.ROOT))
  return providerIcon(provider = provider, threadActivity = threadActivity)
}
