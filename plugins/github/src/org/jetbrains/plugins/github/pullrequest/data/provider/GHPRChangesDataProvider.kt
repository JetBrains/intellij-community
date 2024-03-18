// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.future.asDeferred
import org.jetbrains.plugins.github.api.data.GHCommit
import java.util.concurrent.CompletableFuture

interface GHPRChangesDataProvider {

  /**
   * Request for the sync state between current local branch and branch state on the server.
   * Will produce false if local branch has all the commits that are recorded on the server, true otherwise.
   */
  val newChangesInReviewRequest: SharedFlow<Deferred<Boolean>>

  @RequiresEdt
  fun loadChanges(): CompletableFuture<GitBranchComparisonResult>

  @RequiresEdt
  fun loadPatchFromMergeBase(progressIndicator: ProgressIndicator, commitSha: String, filePath: String): CompletableFuture<FilePatch?>

  @RequiresEdt
  fun reloadChanges()

  @RequiresEdt
  fun addChangesListener(disposable: Disposable, listener: () -> Unit)

  @RequiresEdt
  fun loadChanges(disposable: Disposable, consumer: (CompletableFuture<GitBranchComparisonResult>) -> Unit) {
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

fun GHPRChangesDataProvider.changesRequestFlow(): Flow<Deferred<GitBranchComparisonResult>> =
  channelFlow {
    val cs = childScope()
    val listenerDisposable = Disposer.newDisposable()
    val listener: () -> Unit = {
      cs.async {
        try {
          loadChanges().asDeferred().await()
        }
        catch (e: ProcessCanceledException) {
          cancel()
          awaitCancellation()
        }
      }.let {
        trySend(it)
      }
    }
    addChangesListener(listenerDisposable, listener)
    listener()
    awaitClose {
      Disposer.dispose(listenerDisposable)
    }
  }.flowOn(Dispatchers.Main)

fun GHPRChangesDataProvider.fetchedChangesFlow(): Flow<Deferred<GitBranchComparisonResult>> =
  channelFlow {
    val cs = childScope()
    val listenerDisposable = Disposer.newDisposable()
    val listener: () -> Unit = {
      cs.async {
        try {
          //TODO: don't fetch when not necessary
          fetchBaseBranch().asDeferred().await()
          fetchHeadBranch().asDeferred().await()
          loadChanges().asDeferred().await()
        }
        catch (e: ProcessCanceledException) {
          cancel()
          awaitCancellation()
        }
      }.let {
        trySend(it)
      }
    }
    addChangesListener(listenerDisposable, listener)
    listener()
    awaitClose {
      Disposer.dispose(listenerDisposable)
    }
  }.flowOn(Dispatchers.Main)