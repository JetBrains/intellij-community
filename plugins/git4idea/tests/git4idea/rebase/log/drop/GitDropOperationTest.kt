// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log.drop

import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.rebase.log.GitCommitEditingOperationResult

internal class GitDropOperationTest : GitDropOperationTestBase() {
  override fun execute(commitsToDrop: List<VcsCommitMetadata>): GitCommitEditingOperationResult {
    return GitDropOperation(repo).execute(commitsToDrop)
  }
}