// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.withAgentThreadActivityBadge
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.BadgeDotProvider
import com.intellij.ui.BadgeIcon
import com.intellij.ui.IconManager
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

private data class AgentSessionThreadStatusIconKey(
  val provider: AgentSessionProvider?,
  @JvmField val activity: AgentThreadActivity,
  @JvmField val useMonochrome: Boolean,
)

private val THREAD_STATUS_ICON_CACHE = ConcurrentHashMap<AgentSessionThreadStatusIconKey, Icon>()
private val TOOL_WINDOW_MESSAGES_FALLBACK_ICON: Icon by lazy {
  val iconManager = IconManager.getInstance()
  runCatching {
    iconManager.getIcon("expui/toolwindows/messages.svg", IconManager::class.java.classLoader)
  }.getOrElse {
    iconManager.getIcon("toolwindows/toolWindowMessages.svg", IconManager::class.java.classLoader)
  }
}

private fun resolveAgentSessionProviderIcon(provider: AgentSessionProvider?, useMonochrome: Boolean): Icon? {
  return provider?.let {
    val descriptor = AgentSessionProviders.find(it) ?: return@let null
    if (useMonochrome) descriptor.monochromeIcon else descriptor.icon
  }
}

@ApiStatus.Internal
fun agentSessionThreadStatusIcon(provider: AgentSessionProvider?, activity: AgentThreadActivity): Icon {
  val useMonochrome = Registry.`is`("agent.workbench.use.monochrome.icons", true)
  val key = AgentSessionThreadStatusIconKey(provider = provider, activity = activity, useMonochrome = useMonochrome)
  return THREAD_STATUS_ICON_CACHE.computeIfAbsent(key) {
    withAgentThreadActivityBadge(resolveAgentSessionProviderIcon(provider, useMonochrome) ?: TOOL_WINDOW_MESSAGES_FALLBACK_ICON, activity)
  }
}

@ApiStatus.Internal
fun agentSessionThreadStatusIcon(baseIcon: Icon?, activity: AgentThreadActivity): Icon {
  return withAgentThreadActivityBadge(baseIcon ?: TOOL_WINDOW_MESSAGES_FALLBACK_ICON, activity)
}

@ApiStatus.Internal
fun withYoloModeBadge(baseIcon: Icon): Icon {
  return BadgeIcon(baseIcon, JBUI.CurrentTheme.IconBadge.ERROR, BadgeDotProvider())
}

@ApiStatus.Internal
@TestOnly
fun clearAgentSessionThreadStatusIconCacheForTests() {
  THREAD_STATUS_ICON_CACHE.clear()
}
