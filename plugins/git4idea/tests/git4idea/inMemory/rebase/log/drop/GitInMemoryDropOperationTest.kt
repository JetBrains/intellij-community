// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log.drop

import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitDisposable
import git4idea.inMemory.rebase.log.InMemoryRebaseOperations
import git4idea.log.createLogDataIn
import git4idea.log.refreshAndWait
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.drop.GitDropOperationTestBase
import kotlinx.coroutines.runBlocking

internal class GitInMemoryDropOperationTest : GitDropOperationTestBase() {
  override fun execute(commitsToDrop: List<VcsCommitMetadata>): GitCommitEditingOperationResult {
    return runBlocking {
      val testCs = GitDisposable.getInstance(project).coroutineScope
      val logData = createLogDataIn(testCs, repo, logProvider)
      logData.refreshAndWait(repo, true)
      InMemoryRebaseOperations.drop(repo, logData, commitsToDrop)
    }
  }
}