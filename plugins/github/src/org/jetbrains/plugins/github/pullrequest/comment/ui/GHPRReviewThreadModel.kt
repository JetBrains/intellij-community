// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.ui.CollectionListModel
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import kotlin.properties.Delegates.observable

class GHPRReviewThreadModel(thread: GHPullRequestReviewThread)
  : CollectionListModel<GHPRReviewCommentModel>(thread.comments.map(GHPRReviewCommentModel.Companion::convert)) {

  private val collapseStateEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)


  val id: String = thread.id
  val createdAt = thread.createdAt
  val filePath = thread.path
  val diffHunk = thread.diffHunk

  var fold by observable(true) { _, _, _ ->
    collapseStateEventDispatcher.multicaster.eventOccurred()
  }

  // New comments can only appear at the end of the list and cannot change order
  fun update(thread: GHPullRequestReviewThread) {
    var removed = 0
    for (i in 0 until items.size) {
      val idx = i - removed
      val newComment = thread.comments.getOrNull(idx)
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

    if (size == thread.comments.size) return
    val newComments = thread.comments.subList(size, thread.comments.size)
    add(newComments.map { GHPRReviewCommentModel.convert(it) })
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
    if (other !is GHPRReviewThreadModel) return false

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }
}