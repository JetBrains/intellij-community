// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui

import com.intellij.collaboration.ui.codereview.comment.CommentBackspaceHandler


internal class GitlabMarkdownBackspaceHandler : CommentBackspaceHandler<GitLabViewModelWithTextCompletion>() {
  override fun getKey() = GitLabViewModelWithTextCompletion.MENTIONS_COMPLETION_KEY
  override fun isValidMentionCharacter(c: Char) = c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '@'
}
