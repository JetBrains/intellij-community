// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.ui.components.JBLoadingPanel
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class VcsLogCommitSelectionListenerForDiff(
  private val changesLoadingPane: JBLoadingPanel,
  private val changesTreeModel: VcsLogAsyncChangesTreeModel,
) : CommitDetailsLoader.Listener<VcsFullCommitDetails> {
  override fun onEmptySelection() {
    changesTreeModel.setSelectedDetails(emptyList())
  }

  override fun onDetailsLoaded(commitIds: List<VcsLogCommitStorageIndex>, details: List<VcsFullCommitDetails>) {
    changesTreeModel.setSelectedDetails(details)
  }

  override fun onSelection() {
    changesTreeModel.setEmptySelection()
  }

  override fun onLoadingStarted() {
    changesLoadingPane.startLoading()
  }

  override fun onLoadingStopped() {
    changesLoadingPane.stopLoading()
  }

  override fun onError(error: Throwable) {
    changesTreeModel.setSelectionError()
  }
}
