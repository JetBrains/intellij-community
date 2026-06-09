// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui

import com.intellij.collaboration.ui.codereview.comment.CommentBackspaceHandler
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion


internal class GithubMarkdownBackspaceHandler : CommentBackspaceHandler<GHViewModelWithTextCompletion>() {
  override fun getKey() = GHViewModelWithTextCompletion.MENTIONS_COMPLETION_KEY
  override fun isValidMentionCharacter(c: Char) = c.isLetterOrDigit() || c == '-' || c == '@'
}
