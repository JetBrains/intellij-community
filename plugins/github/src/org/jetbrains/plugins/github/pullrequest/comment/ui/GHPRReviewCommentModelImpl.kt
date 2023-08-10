// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.util.EventDispatcher
import com.intellij.vcsUtil.Delegates.equalVetoingObservable
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment

class GHPRReviewCommentModelImpl(comment: GHPullRequestReviewComment) : GHPRReviewCommentModel {

  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override val id = comment.id
  override val canBeDeleted = comment.viewerCanDelete
  override val canBeUpdated = comment.viewerCanUpdate

  override var state = comment.state
    private set
  override var dateCreated = comment.createdAt
    private set
  override var body = comment.body
    private set
  override val author = comment.author

  override var isFirstInResolvedThread by equalVetoingObservable(false) {
    changeEventDispatcher.multicaster.eventOccurred()
  }

  init {
    update(comment)
  }

  override fun update(comment: GHPullRequestReviewComment): Boolean {
    if (comment.id != id) throw IllegalArgumentException("Can't update comment data from different comment")

    var updated = false

    if (state != comment.state)
      updated = true
    state = comment.state

    dateCreated = comment.createdAt

    if (body != comment.body)
      updated = true
    body = comment.body

    if (updated) changeEventDispatcher.multicaster.eventOccurred()
    return updated
  }

  override fun addAndInvokeChangesListener(listener: () -> Unit) = SimpleEventListener.addAndInvokeListener(changeEventDispatcher, listener)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRReviewCommentModelImpl) return false

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  companion object {
    fun convert(comment: GHPullRequestReviewComment): GHPRReviewCommentModelImpl =
      GHPRReviewCommentModelImpl(comment)
  }
}
