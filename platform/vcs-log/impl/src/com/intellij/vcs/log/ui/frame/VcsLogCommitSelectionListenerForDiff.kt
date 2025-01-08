// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.StatusText
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.util.VcsLogUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class VcsLogCommitSelectionListenerForDiff(
  private val changesLoadingPane: JBLoadingPanel,
  private val changesBrowser: VcsLogChangesBrowser
) : CommitDetailsLoader.Listener<VcsFullCommitDetails> {
  override fun onEmptySelection() {
    changesBrowser.setSelectedDetails(emptyList())
  }

  override fun onDetailsLoaded(commitIds: List<Int>, details: List<VcsFullCommitDetails>) {
    val maxSize = VcsLogUtil.getMaxSize(details)
    if (maxSize > VcsLogUtil.getShownChangesLimit()) {
      val sizeText = VcsLogUtil.getSizeText(maxSize)
      changesBrowser.setEmptyWithText { statusText: StatusText ->
        statusText.setText(VcsLogBundle.message("vcs.log.changes.too.many.status", details.size, sizeText))
        statusText.appendSecondaryText(VcsLogBundle.message("vcs.log.changes.too.many.show.anyway.status.action"),
                                       SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
        ) { changesBrowser.setSelectedDetails(details) }
      }
    }
    else {
      changesBrowser.setSelectedDetails(details)
    }
  }

  override fun onSelection() {
    changesBrowser.setEmpty()
  }

  override fun onLoadingStarted() {
    changesLoadingPane.startLoading()
  }

  override fun onLoadingStopped() {
    changesLoadingPane.stopLoading()
  }

  override fun onError(error: Throwable) {
    changesBrowser.setEmptyWithText { statusText: StatusText ->
      statusText.setText(VcsLogBundle.message("vcs.log.error.loading.changes.status"))
    }
  }
}
