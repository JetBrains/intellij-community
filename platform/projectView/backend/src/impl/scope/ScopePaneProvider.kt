// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.platform.projectView.backend.impl.scope

import com.intellij.ide.scopeView.NamedScopeFilter
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneProvider
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.util.PlatformUtils
import com.intellij.util.asDisposable
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.milliseconds

internal class ScopePaneProvider : ProjectViewPaneProvider {
  override fun createPanes(project: Project): Flow<List<ProjectViewPaneModel>> {
    // The same IDEs where the legacy ScopeViewPane extension isn't applicable.
    if (PlatformUtils.isPyCharmEducational() || PlatformUtils.isRider()) return flowOf(emptyList())
    return channelFlow {
      val holders: Array<NamedScopesHolder> = arrayOf(
        DependencyValidationManager.getInstance(project),
        NamedScopeManager.getInstance(project),
      )
      // Only the fact that something changed matters, not how many times, hence the conflating channel.
      val scopeChanges = Channel<Unit>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
      val listener = NamedScopesHolder.ScopeListener { scopeChanges.trySend(Unit) }
      // TODO ChangeList scope contents may lag behind: com.intellij.vcs.changes.ChangeListScopeViewUpdater
      //  refreshes the legacy pane directly on changeListsChanged() without firing the scope listeners, so a
      //  file moving between changelists isn't seen here. To be fixed together with ScopeViewTreeModel itself.
      for (holder in holders) {
        holder.addScopeListener(listener, asDisposable())
      }
      scopeChanges.trySend(Unit) // the initial set of scopes
      // Scope changes tend to come in bursts (loading the scope settings, a batch of changelist updates, ...),
      // and each one rebuilds the pane list, so coalesce them the way ScopeViewPane does.
      val manager = ScopePaneManager(project, holders)
      scopeChanges.consumeAsFlow().debounce(10.milliseconds).collect {
        send(manager.updatePanes())
      }
    }
  }
}

private class ScopePaneManager(private val project: Project, private val holders: Array<NamedScopesHolder>) {
  private val panes = hashMapOf<ProjectViewPaneId, ScopePaneModel>()

  suspend fun updatePanes(): List<ProjectViewPaneModel> {
    val filters = readAction { NamedScopeFilter.list(*holders) }
    val result = ArrayList<ProjectViewPaneModel>(filters.size)
    val stillActualIds = hashSetOf<ProjectViewPaneId>()
    for (filter in filters) {
      val id = ScopePaneModel.paneId(filter)
      if (id in stillActualIds) {
        LOG.warn("Duplicate scope $id, only the first one will be shown")
        continue
      }
      stillActualIds += id
      val existingPane = panes[id]
      if (existingPane == null) {
        // A brand-new scope: this is also where a renamed scope shows up, because the name is part of the ID.
        LOG.debug { "A new scope pane: $id" }
        val newPane = ScopePaneModel(project, filter)
        panes[id] = newPane
        result += newPane
      }
      else {
        // The same scope, but possibly a new filter instance if its contents have been edited.
        existingPane.updateFilter(filter)
        result += existingPane
      }
    }
    panes.keys.removeAll {
      val remove = it !in stillActualIds
      if (remove) {
        LOG.debug { "A scope pane is gone: $it" }
      }
      remove
    }
    return result
  }
}

private val LOG = logger<ScopePaneProvider>()
