// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import java.util.*

class GHPRReviewCommentModel(val id: String, dateCreated: Date, body: String,
                             authorUsername: String?, authorLinkUrl: String?, authorAvatarUrl: String?) {

  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  var dateCreated = dateCreated
    private set
  var body = body
    private set
  var authorUsername = authorUsername
    private set
  var authorLinkUrl = authorLinkUrl
    private set
  var authorAvatarUrl = authorAvatarUrl
    private set

  fun update(comment: GHPullRequestReviewComment): Boolean {
    if (comment.id != id) throw IllegalArgumentException("Can't update comment data from different comment")

    var updated = false
    dateCreated = comment.createdAt

    if (body != comment.bodyHtml)
      updated = true
    body = comment.bodyHtml

    if (authorUsername != comment.author?.login)
      updated = true
    authorUsername = comment.author?.login
    authorLinkUrl = comment.author?.url
    authorAvatarUrl = comment.author?.avatarUrl

    if (updated) changeEventDispatcher.multicaster.eventOccurred()
    return updated
  }

  fun addChangesListener(listener: () -> Unit) {
    changeEventDispatcher.addListener(object : SimpleEventListener {
      override fun eventOccurred() {
        listener()
      }
    })
  }

  companion object {
    fun convert(comment: GHPullRequestReviewComment): GHPRReviewCommentModel =
      GHPRReviewCommentModel(comment.id, comment.createdAt, comment.bodyHtml,
                             comment.author?.login, comment.author?.url,
                             comment.author?.avatarUrl)
  }
}