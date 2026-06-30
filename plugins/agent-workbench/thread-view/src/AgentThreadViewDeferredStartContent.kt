// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class AgentThreadViewDeferredStartContent(
  @JvmField val component: JComponent,
  @JvmField val preferredFocusedComponent: JComponent? = null,
  private val disposeContent: () -> Unit = {},
) {
  private var disposed: Boolean = false

  fun dispose() {
    if (disposed) {
      return
    }
    disposed = true
    disposeContent()
  }
}

private val AGENT_THREAD_VIEW_DEFERRED_START_CONTENT_KEY: Key<AgentThreadViewDeferredStartContent> =
  Key.create("agent.workbench.threadView.deferred.start.content")

internal fun AgentThreadViewVirtualFile.deferredStartContent(): AgentThreadViewDeferredStartContent? {
  return getUserData(AGENT_THREAD_VIEW_DEFERRED_START_CONTENT_KEY)
}

internal fun AgentThreadViewVirtualFile.replaceDeferredStartContent(content: AgentThreadViewDeferredStartContent?) {
  val previous = deferredStartContent()
  if (previous === content) {
    return
  }
  putUserData(AGENT_THREAD_VIEW_DEFERRED_START_CONTENT_KEY, content)
  previous?.dispose()
}

internal fun AgentThreadViewVirtualFile.clearDeferredStartContent() {
  replaceDeferredStartContent(null)
}
