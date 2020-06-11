// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.application.runWriteAction
import org.jetbrains.plugins.github.util.successOnEdt
import java.util.concurrent.CompletableFuture

class GHPreLoadingSubmittableTextFieldModel(initialText: String,
                                            preLoader: CompletableFuture<String>,
                                            submitter: (String) -> CompletableFuture<*>)
  : GHSubmittableTextFieldModel(initialText, submitter) {

  init {
    isBusy = true
    preLoader.successOnEdt {
      isBusy = false
      runWriteAction {
        document.setText(it)
      }
    }
  }
}