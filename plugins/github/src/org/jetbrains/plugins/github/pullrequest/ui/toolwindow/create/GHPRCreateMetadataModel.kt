// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import com.intellij.collaboration.ui.SimpleEventListener
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRMetadataModelBase
import com.intellij.collaboration.util.CollectionDelta
import java.util.concurrent.CompletableFuture
import kotlin.properties.Delegates.observable

class GHPRCreateMetadataModel(repositoryDataService: GHPRRepositoryDataService,
                              private val currentUser: GHUser)
  : GHPRMetadataModelBase(repositoryDataService) {

  private val eventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var assignees: List<GHUser> by observable(emptyList()) { _, _, _ ->
    eventDispatcher.multicaster.eventOccurred()
  }
  override var reviewers: List<GHPullRequestRequestedReviewer> by observable(emptyList()) { _, _, _ ->
    eventDispatcher.multicaster.eventOccurred()
  }
  override var labels: List<GHLabel> by observable(emptyList()) { _, _, _ ->
    eventDispatcher.multicaster.eventOccurred()
  }

  override val isEditingAllowed = true

  override fun getAuthor() = currentUser

  override fun adjustAssignees(indicator: ProgressIndicator, delta: CollectionDelta<GHUser>): CompletableFuture<Unit> {
    assignees = ArrayList(delta.newCollection)
    return CompletableFuture.completedFuture(Unit)
  }

  override fun adjustReviewers(indicator: ProgressIndicator,
                               delta: CollectionDelta<GHPullRequestRequestedReviewer>): CompletableFuture<Unit> {
    reviewers = ArrayList(delta.newCollection)
    return CompletableFuture.completedFuture(Unit)
  }

  override fun adjustLabels(indicator: ProgressIndicator, delta: CollectionDelta<GHLabel>): CompletableFuture<Unit> {
    labels = ArrayList(delta.newCollection)
    return CompletableFuture.completedFuture(Unit)
  }

  override fun addAndInvokeChangesListener(listener: () -> Unit) = SimpleEventListener.addAndInvokeListener(eventDispatcher, listener)
}