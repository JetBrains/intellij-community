// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.platform.projectView.backend.impl.scope

import com.intellij.ide.scopeView.NamedScopeFilter
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manages the scope panes of a project: one pane for each scope.
 *
 * The service creates a pane for a new scope, keeps the pane of a scope that is still there, and drops the pane of a scope that is gone.
 * All panes share one [SharedScopeViewTreeModel].
 */
@Service(Service.Level.PROJECT)
internal class ScopePaneService(private val project: Project, coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): ScopePaneService = project.service()
  }

  private val sharedModel = SharedScopeViewTreeModel(project)

  /** `null` until the first list is computed, so that nobody takes the start value for "no scopes". */
  private val currentPanes: StateFlow<List<ScopePaneModel>?> = paneUpdates().stateIn(coroutineScope, SharingStarted.Lazily, null)

  /** All scope panes, in the order of their scopes. Emits a new list after each change of the scopes. */
  val panes: Flow<List<ScopePaneModel>> = currentPanes.filterNotNull()

  /** The current scope panes, in the order of their scopes. Can be called from any thread. */
  fun getPanes(): List<ScopePaneModel> = currentPanes.value.orEmpty()

  private fun paneUpdates(): Flow<List<ScopePaneModel>> = channelFlow {
    val holders: Array<NamedScopesHolder> = arrayOf(
      DependencyValidationManager.getInstance(project),
      NamedScopeManager.getInstance(project),
    )
    // Only the fact that something changed matters, not how many times, hence the conflating channel.
    val scopeChanges = Channel<Unit>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val listener = NamedScopesHolder.ScopeListener { scopeChanges.trySend(Unit) }
    // Only the set of scopes is watched here. The contents of a scope that answers differently over time,
    // such as a changelist one, are refreshed by ScopeViewTreeModel instead, which invalidates its filter
    // whenever the file statuses change.
    for (holder in holders) {
      holder.addScopeListener(listener, asDisposable())
    }
    scopeChanges.trySend(Unit) // the initial set of scopes
    // Scope changes tend to come in bursts (loading the scope settings, a batch of changelist updates, ...),
    // and each one rebuilds the pane list, so coalesce them the way ScopeViewPane does.
    var panesById = emptyMap<ProjectViewPaneId, ScopePaneModel>()
    scopeChanges.consumeAsFlow().debounce(10.milliseconds).collect {
      panesById = updatePanes(holders, panesById)
      send(panesById.values.toList())
    }
  }

  /** Returns the new panes in the order of their scopes. Reuses the pane of a scope that is still there. */
  private suspend fun updatePanes(
    holders: Array<NamedScopesHolder>,
    oldPanesById: Map<ProjectViewPaneId, ScopePaneModel>,
  ): Map<ProjectViewPaneId, ScopePaneModel> {
    val filters = readAction { NamedScopeFilter.list(*holders) }
    val newPanesById = LinkedHashMap<ProjectViewPaneId, ScopePaneModel>(filters.size)
    for (filter in filters) {
      val id = ScopePaneModel.paneId(filter)
      if (id in newPanesById) {
        LOG.warn("Duplicate scope $id, only the first one will be shown")
        continue
      }
      val existingPane = oldPanesById[id]
      if (existingPane == null) {
        // A brand-new scope: this is also where a renamed scope shows up, because the name is part of the ID.
        LOG.debug { "A new scope pane: $id" }
        newPanesById[id] = ScopePaneModel(project, filter, sharedModel)
      }
      else {
        // The same scope, but possibly a new filter instance if its contents have been edited.
        existingPane.updateFilter(filter)
        newPanesById[id] = existingPane
      }
    }
    for (id in oldPanesById.keys - newPanesById.keys) {
      LOG.debug { "A scope pane is gone: $id" }
    }
    return newPanesById
  }
}

private val LOG = logger<ScopePaneService>()
