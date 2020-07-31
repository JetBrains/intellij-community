// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import java.util.concurrent.CompletableFuture

interface GHPRChangesDataProvider {

  @CalledInAwt
  fun loadChanges(): CompletableFuture<GHPRChangesProvider>

  @CalledInAwt
  fun reloadChanges()

  @CalledInAwt
  fun addChangesListener(disposable: Disposable, listener: () -> Unit)

  @CalledInAwt
  fun loadChanges(disposable: Disposable, consumer: (CompletableFuture<GHPRChangesProvider>) -> Unit) {
    addChangesListener(disposable) {
      consumer(loadChanges())
    }
    consumer(loadChanges())
  }

  @CalledInAwt
  fun loadCommitsFromApi(): CompletableFuture<List<GHCommit>>

  @CalledInAwt
  fun addCommitsListener(disposable: Disposable, listener: () -> Unit)

  @CalledInAwt
  fun loadCommitsFromApi(disposable: Disposable, consumer: (CompletableFuture<List<GHCommit>>) -> Unit) {
    addCommitsListener(disposable) {
      consumer(loadCommitsFromApi())
    }
    consumer(loadCommitsFromApi())
  }

  @CalledInAwt
  fun fetchBaseBranch(): CompletableFuture<Unit>

  @CalledInAwt
  fun fetchHeadBranch(): CompletableFuture<Unit>
}