// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import org.jetbrains.plugins.github.pullrequest.comment.ui.model.GithubPullRequestFileCommentsThread
import javax.swing.JComponent

interface GithubPullRequestEditorCommentsThreadComponentFactory {
  fun createComponent(thread: GithubPullRequestFileCommentsThread): JComponent
}