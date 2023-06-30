// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import git4idea.repo.GitRepository

interface GHPRBranchesModel {
  val localRepository: GitRepository
  val baseBranch: String
  val headBranch: String

  fun addAndInvokeChangeListener(listener: () -> Unit)
}
