// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log

import git4idea.inMemory.GitObjectRepository
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.test.GitSingleRepoTest
import kotlinx.coroutines.runBlocking
import java.util.Collections.synchronizedList

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

  protected fun capturePostRewrites(): List<PostRewriteInvocation> {
    val captures = synchronizedList(mutableListOf<PostRewriteInvocation>())
    git.runHookListener = { _, hookName, _, stdinLines ->
      if (hookName == "post-rewrite") {
        captures += PostRewriteInvocation(stdinLines.map {
          val parts = it.split(' ', limit = 2)
          RewrittenCommit(oldHash = parts[0], newHash = parts[1])
        })
      }
    }
    return captures
  }

  protected data class RewrittenCommit(val oldHash: String, val newHash: String)
  protected data class PostRewriteInvocation(val mappings: List<RewrittenCommit>)
}