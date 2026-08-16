// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log.drop

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.test.GitSingleRepoContext
import git4idea.test.runUnderProgress

@TestApplication
internal class GitDropOperationTest : GitDropOperationTestBase() {
  override fun GitSingleRepoContext.execute(commitsToDrop: List<VcsCommitMetadata>): GitCommitEditingOperationResult {
    return runUnderProgress { GitDropOperation(repo).execute(commitsToDrop) }
  }
}
