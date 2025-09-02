// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.coroutineToIndicator
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitHandlerInputProcessorUtil
import git4idea.commands.GitLineHandler
import git4idea.fetch.GitFetchSupport
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.StandardCharsets

@ApiStatus.Experimental
object GitCodeReviewUtils {
  suspend fun fetch(repository: GitRepository, remote: GitRemote, refspec: String) {
    withContext(Dispatchers.IO) {
      coroutineToIndicator {
        GitFetchSupport.fetchSupport(repository.project).fetch(repository, remote, refspec).throwExceptionIfFailed()
      }
    }
  }

  suspend fun testRevisionsExist(repository: GitRepository, revisions: List<String>) =
    withContext(Dispatchers.IO) {
      val h = GitLineHandler(repository.project, repository.root, GitCommand.CAT_FILE)
      h.setSilent(true)
      h.addParameters("--batch-check=%(objecttype)")
      h.endOptions()
      h.setInputProcessor(GitHandlerInputProcessorUtil.writeLines(revisions, StandardCharsets.UTF_8))

      !Git.getInstance().runCommand(h).getOutputOrThrow().contains("missing")
    }

  suspend fun testIsAncestor(repository: GitRepository, potentialAncestorRev: String, rev: String): Boolean =
    withContext(Dispatchers.IO) {
      val h = GitLineHandler(repository.project, repository.root, GitCommand.MERGE_BASE)
      h.setSilent(true)
      h.addParameters("--is-ancestor", potentialAncestorRev, rev)
      Git.getInstance().runCommand(h).success()
    }
}