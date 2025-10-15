// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesViewId
import com.intellij.openapi.vcs.changes.ChangesViewSplitComponentBinding
import com.intellij.openapi.vcs.changes.CommitChangesViewWithToolbarPanel
import com.intellij.openapi.vcs.changes.LocalChangesListView
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.vcs.impl.shared.RdLocalChanges
import com.intellij.ui.split.createComponent
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.changes.viewModel.BackendCommitChangesViewModel
import com.intellij.vcs.changes.viewModel.BackendLocalCommitChangesViewModel
import com.intellij.vcs.changes.viewModel.BackendRemoteCommitChangesViewModel
import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

/**
 * Abstraction over the Changes view on the backend side.
 *
 * In monolith mode [changesPanel] is the actual panel that renders the local changes tree and toolbar.
 * In split mode it contains a placeholder produced via [ChangesViewSplitComponentBinding].
 *
 * Note that panels implementation is slightly different in monolith and split modes.
 * See [CommitChangesViewWithToolbarPanel] inheritors for more details.
 *
 * Besides different component implementation, currently [viewModel] also depends on the execution mode
 * (see [BackendLocalCommitChangesViewModel] and [BackendRemoteCommitChangesViewModel] for more details).
 * However, once [BackendRemoteCommitChangesViewModel] is stable enough, it should become the only implementation.
 */
internal class BackendChangesView private constructor(
  val changesPanel: JComponent,
  val viewModel: BackendCommitChangesViewModel,
) {
  companion object {
    @JvmStatic
    fun create(project: Project, scope: CoroutineScope): BackendChangesView {
      val model = project.service<BackendCommitChangesViewService>().init(scope)
      val component = when (model) {
        is BackendLocalCommitChangesViewModel -> model.panel
        is BackendRemoteCommitChangesViewModel -> {
          val id = storeValueGlobally(scope, Unit, BackendChangesViewValueIdType)
          ChangesViewSplitComponentBinding.createComponent(project, scope, id)
        }
      }
      return BackendChangesView(component, model)
    }
  }
}

@Service(Service.Level.PROJECT)
internal class BackendCommitChangesViewService(private val project: Project) {
  lateinit var viewModel: BackendCommitChangesViewModel
    private set

  @RequiresEdt
  fun init(scope: CoroutineScope): BackendCommitChangesViewModel {
    if (::viewModel.isInitialized) return viewModel

    viewModel =
      if (RdLocalChanges.isEnabled()) BackendRemoteCommitChangesViewModel(project, scope)
      else {
        val panel = CommitChangesViewWithToolbarPanel(LocalChangesListView(project), scope)
        BackendLocalCommitChangesViewModel(panel)
      }

    return viewModel
  }

  companion object {
    fun getInstance(project: Project): BackendCommitChangesViewService = project.service()
  }
}

private object BackendChangesViewValueIdType : BackendValueIdType<ChangesViewId, Unit>(::ChangesViewId)