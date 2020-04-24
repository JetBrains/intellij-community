// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import java.util.concurrent.CompletableFuture

interface GHPRDetailsDataProvider {

  @get:CalledInAwt
  val loadedDetails: GHPullRequest?

  @CalledInAwt
  fun loadDetails(): CompletableFuture<GHPullRequest>

  @CalledInAwt
  fun reloadDetails()

  @CalledInAwt
  fun addDetailsReloadListener(disposable: Disposable, listener: () -> Unit)

  @CalledInAwt
  fun loadDetails(disposable: Disposable, consumer: (CompletableFuture<GHPullRequest>) -> Unit) {
    addDetailsReloadListener(disposable) {
      consumer(loadDetails())
    }
    consumer(loadDetails())
  }

  @CalledInAwt
  fun addDetailsLoadedListener(disposable: Disposable, listener: () -> Unit)
}