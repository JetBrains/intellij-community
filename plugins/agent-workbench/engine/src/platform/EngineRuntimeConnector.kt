// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.core.ThreadId
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Re-binds a restored structured thread to its runtime owner. Called when a structured threadView tab is
 * restored, so the runtime adapter can rehydrate ownership from the persisted
 * `ThreadProjection.runtimeBinding` off the UI thread and re-enable prompt input — without the
 * community Engine UI depending on a specific runtime adapter (e.g. ACP).
 */
@ApiStatus.Internal
interface EngineRuntimeConnector {
  /**
   * Eagerly rehydrate runtime ownership for [threadId]. Implementations must return quickly (offload any
   * projection read, catalog lookup, or process work to a background scope) and may be called repeatedly.
   * Long-running restore work must be disposed with [parent].
   */
  fun connectOnRestore(project: Project, threadId: ThreadId, parent: Disposable)

  companion object {
    private val EP = ExtensionPointName<EngineRuntimeConnector>("com.intellij.agent.workbench.engine.runtimeConnector")

    /** Asks every registered connector to rehydrate ownership of [threadId]. */
    fun connectAll(project: Project, threadId: ThreadId, parent: Disposable) {
      val connectors = EP.extensionsIfPointIsRegistered
      LOG.info("[$threadId] Engine runtime restore connectors: count=${connectors.size}")
      connectors.forEach { connector ->
        runCatching { connector.connectOnRestore(project, threadId, parent) }
          .onFailure { LOG.warn("[$threadId] Engine runtime restore connector failed: ${connector.javaClass.name}", it) }
      }
    }

    private val LOG = logger<EngineRuntimeConnector>()
  }
}
