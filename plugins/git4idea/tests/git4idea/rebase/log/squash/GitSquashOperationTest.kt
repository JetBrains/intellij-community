// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log.squash

import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.test.runUnderProgress

internal class GitSquashOperationTest : GitSquashOperationTestBase() {
  override fun execute(commitsToSquash: List<VcsCommitMetadata>, newMessage: String): GitCommitEditingOperationResult {
    return runUnderProgress { GitSquashOperation(repo).execute(commitsToSquash, newMessage) }
  }
}