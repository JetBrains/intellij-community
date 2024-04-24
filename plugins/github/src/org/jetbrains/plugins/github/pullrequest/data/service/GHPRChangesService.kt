// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.progress.coroutineToIndicator
import git4idea.changes.GitBranchComparisonResult
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

interface GHPRChangesService {

  suspend fun fetch(refspec: String)

  suspend fun isAncestor(potentialAncestorRev: String, rev: String): Boolean

  suspend fun loadCommitsFromApi(pullRequestId: GHPRIdentifier): Collection<GHCommit>

  /**
   * Load patch file of a diff between two refs
   */
  suspend fun loadPatch(ref1: String, ref2: String): List<FilePatch>

  suspend fun loadMergeBaseOid(baseRefOid: String, headRefOid: String): String

  suspend fun createChangesProvider(id: GHPRIdentifier,
                                    baseRef: String,
                                    mergeBaseRef: String,
                                    headRef: String,
                                    commits: List<GHCommit>): GitBranchComparisonResult
}