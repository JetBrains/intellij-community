// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.*
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.vcs.impl.shared.RdLocalChanges
import com.intellij.ui.split.createComponent
import com.intellij.vcs.changes.viewModel.BackendRemoteCommitChangesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import javax.swing.JComponent

internal class BackendChangesView private constructor(
  val changesPanel: JComponent,
  val viewModel: BackendCommitChangesViewModel,
) {
  companion object {
    @JvmStatic
    fun create(project: Project, parentDisposable: Disposable): BackendChangesView {
      val scope = project.service<ScopeProvider>().cs.childScope("CommitChangesViewWithToolbarPanel")
      Disposer.register(parentDisposable) { scope.cancel() }

      if (RdLocalChanges.isEnabled()) {
        val viewModel = BackendRemoteCommitChangesViewModel(project)
        val id = storeValueGlobally(scope, viewModel, BackendChangesViewValueIdType)
        val panel = ChangesViewSplitComponentBinding.createComponent(project, scope, id)
        return BackendChangesView(panel, viewModel)
      }
      else {
        val panel = CommitChangesViewWithToolbarPanel(LocalChangesListView(project), scope)
        val backendChangesView = BackendChangesView(panel, BackendLocalCommitChangesViewModel(panel))
        val id = storeValueGlobally(scope, backendChangesView.viewModel, BackendChangesViewValueIdType)
        panel.id = id
        return backendChangesView
      }
    }
  }

  @Service(Service.Level.PROJECT)
  internal class ScopeProvider(val cs: CoroutineScope)
}