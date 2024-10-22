// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.async.computationStateFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.util.ComputedResult
import com.intellij.openapi.diff.impl.patch.FilePatch
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHCommit
import java.util.concurrent.CompletableFuture

interface GHPRChangesDataProvider {

  val changesNeedReloadSignal: Flow<Unit>

  suspend fun loadCommits(): List<GHCommit>

  suspend fun loadChanges(): GitBranchComparisonResult

  suspend fun ensureAllRevisionsFetched()

  suspend fun loadPatchFromMergeBase(commitSha: String, filePath: String): FilePatch?

  suspend fun signalChangesNeedReload()

  @Deprecated("Please migrate ro coroutines and use loadCommits")
  fun loadCommitsFromApi(): CompletableFuture<List<GHCommit>>
}

@ApiStatus.Internal
fun GHPRChangesDataProvider.changesComputationState(): Flow<ComputedResult<GitBranchComparisonResult>> =
  computationStateFlow(changesNeedReloadSignal.withInitial(Unit)) { loadChanges() }
