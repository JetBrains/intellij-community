// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.vcs.changes.ChangesViewDiffAction
import com.intellij.platform.project.ProjectId
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewDiffApi
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewDiffableSelection
import com.intellij.vcs.changes.viewModel.getRpcChangesView
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScoped

internal class ChangesViewDiffApiImpl : ChangesViewDiffApi {
  override suspend fun performDiffAction(projectId: ProjectId, action: ChangesViewDiffAction) = projectScoped(projectId) { project ->
    LOG.trace { "Received diff action from frontend: $action" }
    project.getRpcChangesView().diffRequests.emit(action to ClientId.current)
  }

  override suspend fun notifySelectionUpdated(projectId: ProjectId, selection: ChangesViewDiffableSelection?) = projectScoped(projectId) { project ->
    LOG.trace { "Selection updated for diff: $selection" }
    project.getRpcChangesView().selectionUpdated(selection)
  }

  companion object {
    private val LOG = logger<ChangesViewDiffApiImpl>()
  }
}
