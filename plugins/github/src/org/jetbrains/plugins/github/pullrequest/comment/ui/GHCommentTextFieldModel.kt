// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.async.CompletableFutureUtil.completionOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.errorOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldModelBase
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture

class GHCommentTextFieldModel(
  project: Project,
  initialText: String,
  private val submitter: (String) -> CompletableFuture<*>
) : CommentTextFieldModelBase(project, initialText) {

  constructor(project: Project, submitter: (String) -> CompletableFuture<*>) : this(project, "", submitter)

  override fun submit() {
    if (isBusy) return

    isBusy = true
    content.isReadOnly = true
    submitter(content.text).successOnEdt {
      content.isReadOnly = false
      content.clear()
    }.errorOnEdt {
      content.isReadOnly = false
      error = it
    }.completionOnEdt {
      isBusy = false
    }
  }
}