// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import java.util.concurrent.CompletableFuture

interface GHPRChangesDataProvider {

  @RequiresEdt
  fun loadChanges(): CompletableFuture<GHPRChangesProvider>

  @RequiresEdt
  fun reloadChanges()

  @RequiresEdt
  fun addChangesListener(disposable: Disposable, listener: () -> Unit)

  @RequiresEdt
  fun loadChanges(disposable: Disposable, consumer: (CompletableFuture<GHPRChangesProvider>) -> Unit) {
    addChangesListener(disposable) {
      consumer(loadChanges())
    }
    consumer(loadChanges())
  }

  @RequiresEdt
  fun loadCommitsFromApi(): CompletableFuture<List<GHCommit>>

  @RequiresEdt
  fun addCommitsListener(disposable: Disposable, listener: () -> Unit)

  @RequiresEdt
  fun loadCommitsFromApi(disposable: Disposable, consumer: (CompletableFuture<List<GHCommit>>) -> Unit) {
    addCommitsListener(disposable) {
      consumer(loadCommitsFromApi())
    }
    consumer(loadCommitsFromApi())
  }

  @RequiresEdt
  fun fetchBaseBranch(): CompletableFuture<Unit>

  @RequiresEdt
  fun fetchHeadBranch(): CompletableFuture<Unit>
}