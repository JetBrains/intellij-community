// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.core.ThreadId
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Sends a user prompt into a Engine thread, starting/continuing the underlying runtime session.
 *
 * Lets the IDE-native ACP screen (in the community engine UI) drive a runtime that lives in another
 * module (e.g. the Ultimate ACP runtime) without a compile-time dependency on it.
 */
@ApiStatus.Internal
interface EnginePromptSender {
  /** Whether this sender owns [threadId] in [project], for example because it prepared/launched it. */
  fun handles(project: Project, threadId: ThreadId): Boolean = false

  /** Sends [text] as the next user prompt in [threadId]; connects the runtime session on first use. */
  fun sendPrompt(project: Project, threadId: ThreadId, text: String)

  companion object {
    private val EP = ExtensionPointName<EnginePromptSender>("com.intellij.agent.workbench.engine.promptSender")

    /** A sender that owns [threadId], if any. */
    fun forThread(project: Project, threadId: ThreadId): EnginePromptSender? {
      val senders = EP.extensionsIfPointIsRegistered
      val sender = senders.firstOrNull { it.handles(project, threadId) }
      LOG.info("[$threadId] Engine prompt sender lookup: count=${senders.size}, found=${sender?.javaClass?.name}")
      return sender
    }

    private val LOG = logger<EnginePromptSender>()
  }
}
