// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Supplies non-terminal content for an Agent Chat tab. Providers that host their session UI inside the
 * IDE (e.g. ACP) render a custom component in the same editor tab instead of an embedded
 * terminal. Resolved by provider; when one is present, the chat editor installs the component and skips
 * the terminal lifecycle entirely.
 */
@ApiStatus.Internal
interface AgentChatCustomContentProvider {
  val provider: AgentSessionProvider

  /**
   * Builds the content for a chat tab bound to [threadId] (and its [threadIdentity]). [parent] is the
   * editor's disposable; tie all subscriptions/coroutines to it.
   */
  fun createComponent(
    project: Project,
    threadIdentity: String,
    threadId: String,
    parent: Disposable,
  ): JComponent
}

@ApiStatus.Internal
interface AgentChatPreferredFocusableContent {
  val preferredFocusedComponent: JComponent?
}

@ApiStatus.Internal
object AgentChatCustomContent {
  private val EP: ExtensionPointName<AgentChatCustomContentProvider> =
    ExtensionPointName("com.intellij.agent.workbench.chatCustomContent")

  /** The custom-content provider registered for [provider], or `null` to use the default terminal content. */
  fun find(provider: AgentSessionProvider): AgentChatCustomContentProvider? {
    return EP.extensionsIfPointIsRegistered.firstOrNull { it.provider == provider }
  }
}
