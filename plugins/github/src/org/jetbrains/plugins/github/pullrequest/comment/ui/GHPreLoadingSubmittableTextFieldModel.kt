// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.impl.DocumentImpl
import org.jetbrains.plugins.github.util.completionOnEdt
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import java.util.concurrent.CompletableFuture

class GHPreLoadingSubmittableTextFieldModel(initialText: String,
                                            preLoader: CompletableFuture<String>,
                                            submitter: (String) -> CompletableFuture<*>)
  : GHSubmittableTextFieldModel(initialText, submitter) {

  init {
    document as DocumentImpl
    document.setAcceptSlashR(true)
    document.setReadOnly(true)
    isBusy = true
    preLoader.successOnEdt {
      document.setReadOnly(false)
      runWriteAction {
        document.setText(it)
      }
    }.errorOnEdt {
      error = it
    }.completionOnEdt {
      isBusy = false
    }
  }
}