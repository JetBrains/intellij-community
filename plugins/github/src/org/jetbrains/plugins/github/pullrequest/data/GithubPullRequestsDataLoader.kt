// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.google.common.cache.CacheBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.util.EventDispatcher
import git4idea.commands.Git
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.api.GithubServerPath
import java.util.*

internal class GithubPullRequestsDataLoader(private val project: Project,
                                            private val progressManager: ProgressManager,
                                            private val git: Git,
                                            private val requestExecutor: GithubApiRequestExecutor,
                                            private val repository: GitRepository,
                                            private val remote: GitRemote,
                                            private val serverPath: GithubServerPath,
                                            private val repoPath: GithubFullPath) : Disposable {

  private var isDisposed = false
  private val cache = CacheBuilder.newBuilder()
    .removalListener<Long, GithubPullRequestDataProviderImpl> {
      Disposer.dispose(it.value)
      invalidationEventDispatcher.multicaster.providerChanged(it.key)
    }
    .maximumSize(5)
    .build<Long, GithubPullRequestDataProviderImpl>()

  private val invalidationEventDispatcher = EventDispatcher.create(ProviderChangedListener::class.java)

  @CalledInAwt
  fun reloadDetails(number: Long) {
    cache.getIfPresent(number)?.reloadDetails()
  }

  @CalledInAwt
  fun invalidateData(number: Long) {
    cache.invalidate(number)
  }

  @CalledInAwt
  fun invalidateAllData() {
    cache.invalidateAll()
  }

  @CalledInAwt
  fun getDataProvider(number: Long): GithubPullRequestDataProvider {
    if (isDisposed) throw IllegalStateException("Already disposed")

    return cache.get(number) {
      val provider = GithubPullRequestDataProviderImpl(project, progressManager, git, requestExecutor, repository, remote, serverPath,
                                                       repoPath.user, repoPath.repository, number)
      provider.load()
      provider
    }
  }

  fun addProviderChangesListener(listener: ProviderChangedListener, disposable: Disposable) =
    invalidationEventDispatcher.addListener(listener, disposable)

  override fun dispose() {
    invalidateAllData()
    isDisposed = true
  }

  interface ProviderChangedListener : EventListener {
    fun providerChanged(pullRequestNumber: Long)
  }
}