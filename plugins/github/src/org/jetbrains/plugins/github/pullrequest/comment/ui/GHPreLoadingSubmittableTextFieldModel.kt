// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.async.CompletableFutureUtil.completionOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.errorOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture

class GHPreLoadingSubmittableTextFieldModel(
  project: Project,
  initialText: String,
  preLoader: CompletableFuture<String>,
  submitter: (String) -> CompletableFuture<*>
) : GHSubmittableTextFieldModel(project, initialText, submitter) {

  init {
    content.isAcceptSlashR = true
    content.isReadOnly = true
    isBusy = true
    preLoader.successOnEdt {
      content.isReadOnly = false
      content.text = it
    }.errorOnEdt {
      error = it
    }.completionOnEdt {
      isBusy = false
    }
  }
}