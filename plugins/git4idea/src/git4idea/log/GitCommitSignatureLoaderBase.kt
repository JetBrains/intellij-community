// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.data.util.VcsCommitsDataLoader
import git4idea.commit.signature.GitCommitSignature
import git4idea.repo.GitRepositoryManager
import kotlin.properties.Delegates.observable

internal abstract class GitCommitSignatureLoaderBase(private val project: Project)
  : VcsCommitsDataLoader<GitCommitSignature>, Disposable {

  private var currentIndicator by observable<ProgressIndicator?>(null) { _, old, _ ->
    old?.cancel()
  }

  final override fun loadData(commits: List<CommitId>, onChange: (Map<CommitId, GitCommitSignature>) -> Unit) {
    currentIndicator = EmptyProgressIndicator()
    val indicator = currentIndicator ?: return

    val commitsByRoot = commits.groupBy({ it.root }, { it.hash }).filter { (root, _) ->
      GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root) != null
    }
    if (commitsByRoot.isEmpty()) return

    requestData(indicator, commitsByRoot, onChange)
  }

  protected abstract fun requestData(indicator: ProgressIndicator,
                                     commits: Map<VirtualFile, List<Hash>>,
                                     onChange: (Map<CommitId, GitCommitSignature>) -> Unit)

  override fun dispose() {
    currentIndicator = null
  }
}