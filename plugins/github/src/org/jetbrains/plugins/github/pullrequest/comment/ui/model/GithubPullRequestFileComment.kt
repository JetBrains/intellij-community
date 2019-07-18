// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui.model

import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import java.util.*

class GithubPullRequestFileComment(val id: Long, dateCreated: Date, body: String,
                                   authorUsername: String, authorLinkUrl: String, authorAvatarUrl: String?) {

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

  fun update(comment: GithubPullRequestCommentWithHtml): Boolean {
    if (comment.id != id) throw IllegalArgumentException("Can't update comment data from different comment")

    var updated = false
    dateCreated = comment.createdAt

    if (body != comment.bodyHtml)
      updated = true
    body = comment.bodyHtml

    if (authorUsername != comment.user.login)
      updated = true
    authorUsername = comment.user.login
    authorLinkUrl = comment.user.htmlUrl
    authorAvatarUrl = comment.user.avatarUrl

    return updated
  }

  companion object {
    fun convert(comment: GithubPullRequestCommentWithHtml): GithubPullRequestFileComment =
      GithubPullRequestFileComment(comment.id, comment.createdAt, comment.bodyHtml,
                                   comment.user.login, comment.user.htmlUrl, comment.user.avatarUrl)
  }
}