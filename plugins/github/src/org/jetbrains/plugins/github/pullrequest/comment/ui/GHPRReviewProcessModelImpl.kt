// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.util.EventDispatcher
import com.intellij.collaboration.ui.SimpleEventListener
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview

class GHPRReviewProcessModelImpl : GHPRReviewProcessModel {

  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var pendingReview: GHPullRequestPendingReview? = null
    private set
  override var isActual: Boolean = false
    private set

  override fun populatePendingReviewData(review: GHPullRequestPendingReview?) {
    pendingReview = review
    isActual = true
    changeEventDispatcher.multicaster.eventOccurred()
  }

  override fun clearPendingReviewData() {
    pendingReview = null
    isActual = false
    changeEventDispatcher.multicaster.eventOccurred()
  }

  override fun addAndInvokeChangesListener(listener: SimpleEventListener) {
    changeEventDispatcher.addListener(listener)
    listener.eventOccurred()
  }

  override fun removeChangesListener(listener: SimpleEventListener) {
    changeEventDispatcher.removeListener(listener)
  }
}
