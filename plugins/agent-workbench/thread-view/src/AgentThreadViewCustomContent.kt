// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionSurfaceId
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
data class AgentThreadViewContentContext(
  val provider: AgentSessionProvider,
  val surfaceId: AgentSessionSurfaceId,
  @JvmField val launchTargetId: String?,
  @JvmField val threadIdentity: String,
  @JvmField val threadId: String,
)

/**
 * Supplies non-terminal content for an Agent Thread View tab. Providers that host their session UI inside the
 * IDE (e.g. ACP) render a custom component in the same editor tab instead of an embedded
 * terminal. Resolved by launch context; when one is present, the threadView editor installs the component and
 * skips the terminal lifecycle entirely.
 */
@ApiStatus.Internal
interface AgentThreadViewCustomContentProvider {
  val provider: AgentSessionProvider

  fun handles(context: AgentThreadViewContentContext): Boolean = provider == context.provider

  /**
   * Builds the content for a threadView tab bound to [threadId] (and its [threadIdentity]). [parent] is the
   * editor's disposable; tie all subscriptions/coroutines to it.
   */
  fun createComponent(
    project: Project,
    threadIdentity: String,
    threadId: String,
    parent: Disposable,
  ): JComponent

  fun createComponent(
    project: Project,
    context: AgentThreadViewContentContext,
    parent: Disposable,
  ): JComponent {
    return createComponent(
      project = project,
      threadIdentity = context.threadIdentity,
      threadId = context.threadId,
      parent = parent,
    )
  }
}

@ApiStatus.Internal
interface AgentThreadViewPreferredFocusableContent {
  val preferredFocusedComponent: JComponent?
}

@ApiStatus.Internal
object AgentThreadViewCustomContent {
  private val EP: ExtensionPointName<AgentThreadViewCustomContentProvider> =
    ExtensionPointName("com.intellij.agent.workbench.agentThreadViewCustomContent")

  /** The custom-content provider registered for [provider], or `null` to use the default terminal content. */
  fun find(provider: AgentSessionProvider): AgentThreadViewCustomContentProvider? {
    return EP.extensionsIfPointIsRegistered.firstOrNull { it.provider == provider }
  }

  /** The custom-content provider registered for [context], or `null` to use the default terminal content. */
  fun find(context: AgentThreadViewContentContext): AgentThreadViewCustomContentProvider? {
    return EP.extensionsIfPointIsRegistered.firstOrNull { it.handles(context) }
  }
}
