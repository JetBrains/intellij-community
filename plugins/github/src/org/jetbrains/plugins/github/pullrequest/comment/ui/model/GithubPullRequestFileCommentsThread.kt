// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui.model

import com.intellij.ui.CollectionListModel
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.data.model.GithubPullRequestFileCommentsThreadMapping
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import kotlin.properties.Delegates.observable

class GithubPullRequestFileCommentsThread(comments: List<GithubPullRequestFileComment>)
  : CollectionListModel<GithubPullRequestFileComment>(comments) {

  private val collapseStateEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  init {
    if (comments.isEmpty()) throw IllegalArgumentException("Thread cannot be empty")
  }

  val firstCommentId = items.first().id
  val firstCommentCreated = items.first().dateCreated

  var fold by observable(true) { _, _, _ ->
    collapseStateEventDispatcher.multicaster.eventOccurred()
  }

  // New comments can only appear at the end of the list and cannot change order
  fun update(comments: List<GithubPullRequestCommentWithHtml>) {
    var removed = 0
    for (i in 0 until items.size) {
      val idx = i - removed
      val newComment = comments.getOrNull(idx)
      if (newComment == null) {
        removeRange(idx, items.size - 1)
        break
      }

      val comment = items.getOrNull(idx) ?: break
      if (comment.id == newComment.id) {
        if (comment.update(newComment))
          fireContentsChanged(this, idx, idx)
      }
      else {
        remove(idx)
        removed++
      }
    }

    if (size == comments.size) return
    val newComments = comments.subList(size, comments.size)
    add(newComments.map { GithubPullRequestFileComment.convert(it) })
  }

  fun addFoldStateChangeListener(listener: () -> Unit) {
    collapseStateEventDispatcher.addListener(object : SimpleEventListener {
      override fun eventOccurred() {
        listener()
      }
    })
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GithubPullRequestFileCommentsThread) return false

    if (firstCommentId != other.firstCommentId) return false

    return true
  }

  override fun hashCode(): Int {
    return firstCommentId.hashCode()
  }


  companion object {
    fun convert(mapping: GithubPullRequestFileCommentsThreadMapping): GithubPullRequestFileCommentsThread =
      GithubPullRequestFileCommentsThread(mapping.comments.map(GithubPullRequestFileComment.Companion::convert))
  }
}