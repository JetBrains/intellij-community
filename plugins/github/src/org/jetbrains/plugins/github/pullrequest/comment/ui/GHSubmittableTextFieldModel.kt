// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.application.runWriteAction
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextFieldModelBase
import org.jetbrains.plugins.github.util.completionOnEdt
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import java.util.concurrent.CompletableFuture

open class GHSubmittableTextFieldModel(
  initialText: String,
  private val submitter: (String) -> CompletableFuture<*>
) : SubmittableTextFieldModelBase(initialText) {

  constructor(submitter: (String) -> CompletableFuture<*>) : this("", submitter)

  override fun submit() {
    if (isBusy) return

    isBusy = true
    document.setReadOnly(true)
    submitter(document.text).successOnEdt {
      document.setReadOnly(false)
      runWriteAction {
        document.setText("")
      }
    }.errorOnEdt {
      document.setReadOnly(false)
      error = it
    }.completionOnEdt {
      isBusy = false
    }
  }
}