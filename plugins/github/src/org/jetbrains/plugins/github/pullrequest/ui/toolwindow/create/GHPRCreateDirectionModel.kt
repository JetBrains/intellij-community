// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import git4idea.GitBranch
import git4idea.GitRemoteBranch
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping

interface GHPRCreateDirectionModel {
  val baseRepo: GHGitRepositoryMapping
  var baseBranch: GitRemoteBranch?
  val headRepo: GHGitRepositoryMapping?
  val headBranch: GitBranch?

  var headSetByUser: Boolean

  fun setHead(repo: GHGitRepositoryMapping?, branch: GitBranch?)

  fun addAndInvokeDirectionChangesListener(listener: () -> Unit)

}