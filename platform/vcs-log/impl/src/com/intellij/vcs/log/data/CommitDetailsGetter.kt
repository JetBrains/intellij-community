// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogProvider
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.jetbrains.annotations.CalledInAny

/**
 * The CommitDetailsGetter is responsible for getting [complete commit details][VcsFullCommitDetails] from the cache or from the VCS.
 */
class CommitDetailsGetter internal constructor(storage: VcsLogStorage,
                                               logProviders: Map<VirtualFile, VcsLogProvider>,
                                               parentDisposable: Disposable) :
  AbstractDataGetter<VcsFullCommitDetails>(storage, logProviders, parentDisposable) {

  private val cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .build<Int, VcsFullCommitDetails>()
  private val placeholdersCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .weakValues()
    .build<Int, LoadingDetailsImpl> { LoadingDetailsImpl(storage, it, 0) }

  init {
    LowMemoryWatcher.register({ cache.invalidateAll() }, this)
  }

  @CalledInAny
  override fun getCommitData(commitId: Int): VcsFullCommitDetails {
    return getCommitDataIfAvailable(commitId) ?: placeholdersCache.get(commitId)!!
  }

  @CalledInAny
  override fun getCommitDataIfAvailable(commitId: Int) = cache.getIfPresent(commitId)

  @CalledInAny
  override fun getCommitDataIfAvailable(commits: List<Int>) = Int2ObjectOpenHashMap(cache.getAllPresent(commits))

  @CalledInAny
  override fun saveInCache(commit: Int, details: VcsFullCommitDetails) = cache.put(commit, details)

  @Throws(VcsException::class)
  override fun doLoadCommitsDataFromProvider(logProvider: VcsLogProvider,
                                             root: VirtualFile,
                                             hashes: List<String>,
                                             consumer: Consumer<in VcsFullCommitDetails>) {
    logProvider.readFullDetails(root, hashes, consumer)
  }

  override fun dispose() {
    cache.invalidateAll()
    placeholdersCache.invalidateAll()
  }
}