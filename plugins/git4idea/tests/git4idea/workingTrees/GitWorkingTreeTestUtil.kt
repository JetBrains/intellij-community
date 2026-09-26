// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import git4idea.repo.GitRepository
import git4idea.repo.GitWorkingTreeHolderImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal fun GitRepository.ensureWorkingTreesUpToDateForTests() {
  runBlocking {
    withContext(Dispatchers.IO) {
      (workingTreeHolder as GitWorkingTreeHolderImpl).updateState()
    }
  }
}
