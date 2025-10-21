// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log

import git4idea.inMemory.GitObjectRepository
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.test.GitSingleRepoTest
import kotlinx.coroutines.runBlocking

internal abstract class GitInMemoryOperationTest : GitSingleRepoTest() {
  lateinit var objectRepo: GitObjectRepository

  override fun setUp() {
    super.setUp()
    objectRepo = GitObjectRepository(repo)
  }

  protected fun GitInMemoryCommitEditingOperation.run(): GitCommitEditingOperationResult =
    runBlocking {
      this@run.execute()
    }
}