// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.equalVetoingObservable

class GHPRReviewCommentModel(comment: GHPullRequestReviewComment) {

  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  val id = comment.id
  val canBeDeleted = comment.viewerCanDelete
  val canBeUpdated = comment.viewerCanUpdate

  var state = comment.state
    private set
  var dateCreated = comment.createdAt
    private set
  var body = comment.body
    private set
  var authorUsername = comment.author?.login
    private set
  var authorLinkUrl = comment.author?.url
    private set
  var authorAvatarUrl = comment.author?.avatarUrl
    private set

  var isFirstInResolvedThread by equalVetoingObservable(false) {
    changeEventDispatcher.multicaster.eventOccurred()
  }

  init {
    update(comment)
  }

  fun update(comment: GHPullRequestReviewComment): Boolean {
    if (comment.id != id) throw IllegalArgumentException("Can't update comment data from different comment")

    var updated = false

    if (state != comment.state)
      updated = true
    state = comment.state

    dateCreated = comment.createdAt

    if (body != comment.body)
      updated = true
    body = comment.body

    if (authorUsername != comment.author?.login)
      updated = true
    authorUsername = comment.author?.login
    authorLinkUrl = comment.author?.url
    authorAvatarUrl = comment.author?.avatarUrl

    if (updated) changeEventDispatcher.multicaster.eventOccurred()
    return updated
  }

  fun addChangesListener(listener: () -> Unit) = SimpleEventListener.addListener(changeEventDispatcher, listener)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRReviewCommentModel) return false

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  companion object {
    fun convert(comment: GHPullRequestReviewComment): GHPRReviewCommentModel =
      GHPRReviewCommentModel(comment)
  }
}