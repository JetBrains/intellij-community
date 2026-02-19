// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.vcs.log.Hash
import git4idea.GitUtil
import git4idea.history.GitLogUtil
import git4idea.inMemory.rebase.log.InMemoryRebaseOperations
 import git4idea.inMemory.rebase.log.RebaseEntriesSource
import git4idea.rebase.GitSquashedCommitsMessage
import git4idea.rebase.log.GitInteractiveRebaseEntriesProvider
import git4idea.rebase.log.GitRebaseEntryGeneratedUsingLog
import git4idea.repo.GitRepository

internal object GitAmendSpecificCommitSquasher {
  fun squashLastCommitIntoTarget(repository: GitRepository, target: Hash, newMessage: String) {
    val fixupCommit = GitLogUtil.collectMetadata(repository.project, repository.root, listOf(GitUtil.HEAD)).single()
    val targetCommit = GitLogUtil.collectMetadata(repository.project, repository.root, listOf(target.asString())).single()

    check(GitSquashedCommitsMessage.canAutosquash(fixupCommit.fullMessage, setOf(targetCommit.subject)))

    runBlockingCancellable {
      val entries = repository.project.service<GitInteractiveRebaseEntriesProvider>()
          .tryGetEntriesUsingLog(repository, targetCommit)?.plus(GitRebaseEntryGeneratedUsingLog(fixupCommit))
      checkNotNull(entries)

      InMemoryRebaseOperations.squash(repository, listOf(fixupCommit, targetCommit), newMessage, RebaseEntriesSource.Entries(entries))
    }
  }
}