// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log

import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import git4idea.inMemory.GitObjectRepository
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.test.GitPlatformTestContext
import git4idea.test.GitSingleRepoContext
import kotlinx.coroutines.runBlocking
import java.util.Collections.synchronizedList

internal interface GitInMemoryOperationContext : GitSingleRepoContext {
  val objectRepo: GitObjectRepository
}

internal fun TestFixture<GitSingleRepoContext>.gitInMemoryOperationFixture(): TestFixture<GitInMemoryOperationContext> = testFixture {
  val singleRepoContext = init()

  val result = object : GitInMemoryOperationContext, GitSingleRepoContext by singleRepoContext {
    override val objectRepo = GitObjectRepository(singleRepoContext.repo)
  }
  initialized(result) {}
}

internal fun GitInMemoryCommitEditingOperation.run(): GitCommitEditingOperationResult =
  runBlocking {
    this@run.execute()
  }

/**
 * Starts recording `post-rewrite` hook invocations. The returned list is filled in as the hook fires.
 */
internal fun GitPlatformTestContext.capturePostRewrites(): List<PostRewriteInvocation> {
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

internal data class RewrittenCommit(val oldHash: String, val newHash: String)

internal data class PostRewriteInvocation(val mappings: List<RewrittenCommit>)
