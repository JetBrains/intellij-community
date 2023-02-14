// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch

import git4idea.GitBranch
import git4idea.GitRemoteBranch

interface MergeDirectionModel<RepoMapping> {
  val baseRepo: RepoMapping
  var baseBranch: GitRemoteBranch?
  val headRepo: RepoMapping?
  val headBranch: GitBranch?

  var headSetByUser: Boolean

  fun setHead(repo: RepoMapping?, branch: GitBranch?)

  fun addAndInvokeDirectionChangesListener(listener: () -> Unit)

  fun getKnownRepoMappings(): List<RepoMapping>
}