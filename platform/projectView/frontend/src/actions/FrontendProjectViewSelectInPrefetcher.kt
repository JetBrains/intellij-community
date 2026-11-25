// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.actions

import com.intellij.ide.SelectInContext
import com.intellij.ide.impl.SelectInPrefetcher
import com.intellij.ide.projectView.impl.isProjectViewSplit
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneAggregator
import kotlin.time.Duration.Companion.milliseconds

/**
 * In remote development, fetches from the backend the panes that cannot select the context.
 * So [SplitProjectViewPaneSelectInTarget.canSelect] can answer for the backend panes without an RPC.
 *
 * When the registry key [PREFETCH_KEY] is off, this prefetcher has no work.
 * Then the targets of the backend panes can select any context in remote development.
 */
internal class FrontendProjectViewSelectInPrefetcher : SelectInPrefetcher {
  override fun isNeeded(context: SelectInContext): Boolean {
    if (!isProjectViewSplit() || !Registry.`is`(PREFETCH_KEY, true)) return false
    return FrontendProjectViewPaneAggregator.getInstance(context.project).isCanSelectPrefetchNeeded(context)
  }

  override suspend fun prefetch(context: SelectInContext) {
    val timeout = Registry.intValue(PREFETCH_TIMEOUT_KEY, 500).milliseconds
    FrontendProjectViewPaneAggregator.getInstance(context.project).prefetchCanSelect(context, timeout)
  }
}

private const val PREFETCH_KEY = "project.view.toolwindow.split.remdev.select.in.prefetch"
private const val PREFETCH_TIMEOUT_KEY = "project.view.toolwindow.split.remdev.select.in.prefetch.timeout.ms"
