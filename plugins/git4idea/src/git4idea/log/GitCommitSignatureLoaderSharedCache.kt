// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Disposer
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.data.util.VcsCommitsDataLoader
import git4idea.commit.signature.GitCommitSignature
import java.time.Duration

@Service
internal class GitCommitSignatureLoaderSharedCache: Disposable {

  private val cache = Caffeine.newBuilder()
    .expireAfterAccess(Duration.ofSeconds(60))
    .maximumSize(1000)
    .build<CommitId, GitCommitSignature>()

  fun wrapWithCaching(loader: GitCommitSignatureLoaderBase) =
    object: VcsCommitsDataLoader<GitCommitSignature> {
      override fun loadData(commits: List<CommitId>, onChange: (Map<CommitId, GitCommitSignature>) -> Unit) {
        val cached = cache.getAllPresent(commits)
        onChange(cached)
        val toLoad = commits - cached.keys
        loader.loadData(toLoad) {
          cache.putAll(it)
          onChange(it)
        }
      }

      override fun dispose() {
        Disposer.dispose(loader)
      }
    }

  override fun dispose() {
    cache.invalidateAll()
  }
}