// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.impl.scope

import com.intellij.ide.projectView.impl.CompoundProjectViewNodeDecorator
import com.intellij.ide.projectView.impl.CompoundTreeStructureProvider
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.ide.scopeView.ScopeViewTreeModel
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.projectView.impl.ProjectViewPaneViewSettings
import com.intellij.platform.projectView.settings.ProjectViewPaneFileNestingValue
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsAccessor
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsService
import com.intellij.platform.projectView.settings.ProjectViewPaneSortKey
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one [ScopeViewTreeModel] of all scope panes, the same as the one model of the legacy [ScopeViewPane].
 *
 * One pane is live at a time. Its session runs on the model, and the model shows the scope of that pane.
 * The other managed panes are frozen. Their state builders keep the last tree, and nothing updates it.
 * A pane becomes live when it claims the model. Then the pane that was live becomes frozen.
 */
internal class SharedScopeViewTreeModel(private val project: Project) {
  private val modelLock = Mutex()
  private var model: ScopeViewTreeModel? = null
  private var managedPaneCount = 0

  private val mutableLivePane = MutableStateFlow<ScopePaneModel?>(null)

  /** The pane whose session runs on the model. */
  val livePane: StateFlow<ScopePaneModel?> = mutableLivePane.asStateFlow()

  /** The pane that the frontend selected last. */
  @Volatile
  private var selectedPane: ScopePaneModel? = null

  /** A live session holds it, so the next session starts only after the previous one has stopped. */
  private val liveSessionMutex = Mutex()

  /** Makes [pane] live. The session of the previous live pane stops. */
  fun claim(pane: ScopePaneModel) {
    mutableLivePane.value = pane
  }

  /** Makes [pane] the selected pane and claims the model for it. */
  fun select(pane: ScopePaneModel) {
    selectedPane = pane
    claim(pane)
  }

  /** Gives the model back to the selected pane, for example after a Select In has searched another pane. */
  fun claimForSelectedPane() {
    selectedPane?.let { claim(it) }
  }

  /**
   * Runs [session] each time [pane] becomes live, until it is cancelled.
   *
   * [pane] claims the model at the start. While [pane] is frozen, nothing runs.
   */
  suspend fun manage(pane: ScopePaneModel, session: suspend (ScopeViewTreeModel) -> Unit) {
    val model = acquire()
    try {
      claim(pane)
      livePane.collectLatest { livePane ->
        if (livePane !== pane) return@collectLatest
        liveSessionMutex.withLock {
          session(model)
        }
      }
    }
    finally {
      withContext(NonCancellable) {
        release(pane)
      }
    }
  }

  private suspend fun acquire(): ScopeViewTreeModel = modelLock.withLock {
    ++managedPaneCount
    model ?: createModel().also { model = it }
  }

  private suspend fun release(pane: ScopePaneModel) = modelLock.withLock {
    mutableLivePane.compareAndSet(pane, null)
    if (--managedPaneCount == 0) {
      model?.let { Disposer.dispose(it) }
      model = null
      LOG.debug { "Disposed the shared scope tree model" }
    }
  }

  private fun createModel(): ScopeViewTreeModel {
    val model = ScopeViewTreeModel(project, ProjectViewPaneViewSettings(ScopePaneSettingsAccessor(project))).apply {
      setStructureProvider(CompoundTreeStructureProvider.get(project))
      setNodeDecorator(CompoundProjectViewNodeDecorator.get(project))
    }
    LOG.debug { "Created the shared scope tree model" }
    return model
  }
}

/**
 * The settings of a scope pane, read from the settings service.
 *
 * The model reads them in its constructor, before a session puts the settings into its state builder.
 * The settings of a state builder are also empty after its pane stops, and the model can outlive that pane.
 * The values are the same as [ScopePaneModel] puts into a state builder.
 */
private class ScopePaneSettingsAccessor(private val project: Project) : ProjectViewPaneSettingsAccessor {
  private val service: ProjectViewPaneSettingsService
    get() = ProjectViewPaneSettingsService.getInstance(project)

  override fun isOptionSelected(option: ProjectViewPaneOption): Boolean {
    return service.isOptionSelected(option) && service.isOptionEnabled(option) && isOptionSupportedByScopePane(project, option)
  }

  override fun getSortKey(): ProjectViewPaneSortKey = service.getSortKey()

  override fun getFileNesting(): ProjectViewPaneFileNestingValue = service.getFileNesting() // scope panes support file nesting
}

private val LOG = logger<SharedScopeViewTreeModel>()
